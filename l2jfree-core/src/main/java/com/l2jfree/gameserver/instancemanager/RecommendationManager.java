/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.instancemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.network.packets.server.UserInfo;

/**
 * @author Savormix
 * @since 2009-04-20
 */
public final class RecommendationManager
{
	private static final String ADD_RECOMMENDATION_INFO =
			"INSERT INTO character_recommend_data (charId,lastUpdate) VALUES (?,?)";
	private static final String UPDATE_RECOMMENDATION_INFO =
			"UPDATE character_recommend_data SET evaluationAble = ?,evaluationPoints = ?,lastUpdate=? WHERE charId=?";
	private static final String RESTORE_RECOMMENDATION_INFO =
			"SELECT evaluationAble,evaluationPoints,lastUpdate FROM character_recommend_data WHERE charId=?";
	private static final String ADD_RECOMMENDATION_RESTRICTION = "INSERT INTO character_recommends VALUES (?,?)";
	private static final String REMOVE_RECOMMENDATION_RESTRICTIONS = "TRUNCATE TABLE character_recommends";
	private static final String RESTORE_RECOMMENDATION_RESTRICTIONS =
			"SELECT target_id FROM character_recommends WHERE charId=?";
	
	private static final Logger _log = LoggerFactory.getLogger(RecommendationManager.class);
	private static final long DAY = 24 * 3600 * 1000;
	
	private long nextUpdate;
	
	/** @return the only instance of this manager */
	public static RecommendationManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private RecommendationManager()
	{
		Calendar update = Calendar.getInstance();
		if (update.get(Calendar.HOUR_OF_DAY) >= 13)
			update.add(Calendar.DAY_OF_MONTH, 1);
		update.set(Calendar.HOUR_OF_DAY, 13);
		nextUpdate = update.getTimeInMillis();
		ThreadPoolManager.getInstance().schedule(new RecommendationUpdater(), nextUpdate - System.currentTimeMillis());
		_log.info("RecommendationManager: initialized.");
	}
	
	/**
	 * <B>Tries to recommend a player</B>.<BR>
	 * Sends a system message both on failure and success. Adds a session restriction
	 * <I>(and updates the database if saving evaluation restrictions)</I>.
	 * @param evaluator Player giving the evaluation
	 * @param evaluated Player being evaluated
	 */
	public void recommend(L2Player evaluator, L2Player evaluated)
	{
		if (evaluator == null)
			return;
		
		SystemMessageId smi = null;
		if (evaluator.getLevel() < 10)
			smi = SystemMessageId.ONLY_LEVEL_SUP_10_CAN_RECOMMEND;
		else if (evaluator == evaluated)
			smi = SystemMessageId.YOU_CANNOT_RECOMMEND_YOURSELF;
		else if (evaluator.getEvaluations() <= 0)
			smi = SystemMessageId.NO_MORE_RECOMMENDATIONS_TO_HAVE;
		else if (evaluated.getEvalPoints() >= 255)
			smi = SystemMessageId.YOUR_TARGET_NO_LONGER_RECEIVE_A_RECOMMENDATION;
		else if (!evaluator.canEvaluate(evaluated))
			smi = SystemMessageId.THAT_CHARACTER_IS_RECOMMENDED;
		if (smi != null)
		{
			evaluator.sendPacket(smi);
			return;
		}
		
		Connection con = null;
		PreparedStatement ps = null;
		try
		{
			if (Config.ALT_RECOMMEND)
			{
				con = L2DatabaseFactory.getInstance().getConnection(con);
				ps = con.prepareStatement(ADD_RECOMMENDATION_RESTRICTION);
				ps.setInt(1, evaluator.getObjectId());
				ps.setInt(2, evaluated.getObjectId());
				ps.executeUpdate();
				ps.close();
			}
			//ALWAYS. It's the same on retail!
			evaluator.addEvalRestriction(evaluated.getObjectId());
			update(evaluator, evaluator.getEvaluations() - 1, evaluator.getEvalPoints());
			update(evaluated, evaluated.getEvaluations(), evaluated.getEvalPoints() + 1);
			//changed available evaluation count, notify ONLY the evaluator
			//don't remove this again!
			evaluator.sendPacket(new UserInfo(evaluator));
			SystemMessage sm =
					new SystemMessage(SystemMessageId.YOU_HAVE_RECOMMENDED_C1_YOU_HAVE_S2_RECOMMENDATIONS_LEFT);
			sm.addPcName(evaluated);
			sm.addNumber(evaluator.getEvaluations());
			evaluator.sendPacket(sm);
			sm = new SystemMessage(SystemMessageId.YOU_HAVE_BEEN_RECOMMENDED_BY_C1);
			sm.addPcName(evaluator);
			evaluated.sendPacket(sm);
			evaluated.broadcastUserInfo();
		}
		catch (SQLException e)
		{
			_log.error(evaluator.getName() + " failed evaluating player " + evaluated.getName() + "!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * <B>Create an entry in `character_recommend_data`</B>.<BR>
	 * Called just after character creation, but may be also called when restoring player's
	 * evaluation data and the entry is missing.
	 * @param player The newly created player
	 */
	public void onCreate(L2Player player)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement ps = con.prepareStatement(ADD_RECOMMENDATION_INFO);
			ps.setInt(1, player.getObjectId());
			ps.setLong(2, nextUpdate - DAY);
			ps.executeUpdate();
			ps.close();
		}
		catch (SQLException e)
		{
			_log.error("Failed creating recommendation data for " + player.getName() + "!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Called whenever the character is loaded (<I>{@link L2Player#load(int)}</I> is called.
	 * <LI>Restore player's evaluation data, <I>create entry if necessary</I></LI>
	 * <LI>Restore player's evaluated player data (<I>if enabled in config</I>)</LI>
	 * <LI>Update player's evaluation count and points*</LI><BR>
	 * <I>* - for each 24 hours since the last evaluation data update for this player,
	 * player loses 1-3 points</I>
	 * @param player The loaded L2Player
	 */
	public void onJoin(L2Player player)
	{
		Connection con = null;
		PreparedStatement ps = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			ps = con.prepareStatement(RESTORE_RECOMMENDATION_INFO);
			ps.setInt(1, player.getObjectId());
			ResultSet rs = ps.executeQuery();
			if (!rs.next())
			{
				_log.warn("Player " + player.getName() + " did not have recommendation data, creating default entry!");
				onCreate(player);
				rs = ps.executeQuery();
			}
			int evaluations = rs.getInt("evaluationAble");
			int points = rs.getInt("evaluationPoints");
			long lastUpdate = rs.getLong("lastUpdate");
			while (lastUpdate < (nextUpdate - DAY))
			{
				evaluations = getDailyRecommendations(player.getLevel());
				points = getNewEvalPointsQuick(points, getDailyLostPoints(player.getLevel()));
				lastUpdate += DAY;
			}
			update(player, evaluations, points);
			rs.close();
			ps.close();
			if (Config.ALT_RECOMMEND)
			{
				ps = con.prepareStatement(RESTORE_RECOMMENDATION_RESTRICTIONS);
				ps.setInt(1, player.getObjectId());
				rs = ps.executeQuery();
				while (rs.next())
					player.addEvalRestriction(rs.getInt(1));
				rs.close();
				ps.close();
			}
		}
		catch (SQLException e)
		{
			_log.error("Failed loading recommendation data for player " + player.getName() + "!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Set the new player's evaluation points and save to database.
	 * @param player Player being evaluated
	 * @param evalPoints Evaluation point count
	 */
	public void onGmEvaluation(L2Player player, int evalPoints)
	{
		update(player, player.getEvaluations(), evalPoints);
	}
	
	private void update(L2Player player, int recomLeft, int evalPoints)
	{
		Connection con = null;
		PreparedStatement ps = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			ps = con.prepareStatement(UPDATE_RECOMMENDATION_INFO);
			ps.setInt(1, recomLeft);
			ps.setInt(2, evalPoints);
			ps.setLong(3, nextUpdate - DAY);
			ps.setInt(4, player.getObjectId());
			ps.executeUpdate();
			ps.close();
			player.setEvaluationCount(recomLeft);
			player.setEvalPoints(evalPoints);
		}
		catch (SQLException e)
		{
			_log.error("Failed updating player's (" + player.getName() + ") recommendations!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private int getDailyRecommendations(int level)
	{
		if (level >= 40)
			return 9;
		else if (level >= 20)
			return 6;
		else
			return 3;
	}
	
	private int getDailyLostPoints(int level)
	{
		return getDailyRecommendations(level) / 3;
	}
	
	private int getNewEvalPoints(L2Player player)
	{
		return getNewEvalPointsQuick(player.getEvalPoints(), getDailyLostPoints(player.getLevel()));
	}
	
	private int getNewEvalPointsQuick(int current, int lost)
	{
		if ((lost = (current - lost)) > 0)
			return lost;
		else
			return 0;
	}
	
	/**
	 * Updates online player evaluation data each day at 1PM.
	 * Deletes all evaluation restrictions from the database.
	 * @author Savormix
	 */
	private class RecommendationUpdater implements Runnable
	{
		@Override
		public void run()
		{
			Connection con = null;
			PreparedStatement ps = null;
			int rec, pts;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection();
				for (L2Player player : L2World.getInstance().getAllPlayers())
				{
					ps = con.prepareStatement(UPDATE_RECOMMENDATION_INFO);
					rec = getDailyRecommendations(player.getLevel());
					pts = getNewEvalPoints(player);
					ps.setInt(1, rec);
					ps.setInt(2, pts);
					ps.setLong(3, nextUpdate);
					ps.setInt(4, player.getObjectId());
					ps.executeUpdate();
					ps.close();
					player.setEvaluationCount(rec);
					player.setEvalPoints(pts);
					player.cleanEvalRestrictions();
				}
				ps = con.prepareStatement(REMOVE_RECOMMENDATION_RESTRICTIONS);
				ps.executeUpdate();
				ps.close();
			}
			catch (SQLException e)
			{
				_log.error("Failed updating recommendations!", e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
			
			Calendar update = Calendar.getInstance();
			if (update.get(Calendar.HOUR_OF_DAY) >= 13)
				update.add(Calendar.DAY_OF_MONTH, 1);
			update.set(Calendar.HOUR_OF_DAY, 13);
			nextUpdate = update.getTimeInMillis();
			ThreadPoolManager.getInstance().schedule(this, nextUpdate - System.currentTimeMillis());
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final RecommendationManager _instance = new RecommendationManager();
	}
}
