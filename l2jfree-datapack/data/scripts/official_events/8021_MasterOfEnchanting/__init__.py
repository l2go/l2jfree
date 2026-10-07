# Author Psycho(killer1888) / L2jFree

import sys

from com.l2jfree                                  import Config
from com.l2jfree.gameserver.gameobjects.itemcontainer   import Inventory
from com.l2jfree.gameserver.model.quest           import State
from com.l2jfree.gameserver.model.quest           import QuestState
from com.l2jfree.gameserver.model.quest.jython    import QuestJython as JQuest
from com.l2jfree.gameserver.network.packets.server import NpcSay
from com.l2jfree.gameserver.network.packets.server import SystemMessage
from com.l2jfree.tools.random                     import Rnd
from java.lang                                    import System
from java.text                                    import SimpleDateFormat
from java.util                                    import GregorianCalendar

#-------------------- TO MODIFY --------------------

# Start and End date of the event, end date included for the event (Format: YYYYMMDD )
# Retail: 20090630 - 20090714
EVENTSTARTDATE = 20090630
EVENTENDDATE   = 20090714

# Despawn Yogi at end date? Will start a check every 30 minutes to check if event is over and in case unspawn every npc.
# Usefull if you don't have a server reboot everyday.
# Note that their is no auto start for the event. If the server loads the script on the day or after the start day but
# before end date, the event will be considered as started and will automatically spawn Yogi. It won't spawn them
# by itself on midnight the starting day.
AUTODESPAWN = True

# Prevents player under that level to get scrolls, to block people creating accounts and accounts on private servers.
# Retail = 0
ALLOWEDLEVELS = 0

# Rate drop adena. This will influence the scroll/staff prices on Master Yogi.
# If RATEADENA = 0 it will use your actual server config for adena drop rate
# If higher, it will multiply the prices by your value
# Ratail would then be 1
RATEADENA = 1

# As it subsists and it's a known fact that enchant exploit still exists, the script will log user that bring
# Yogi staffs with equal or higher enchant value than this option. (So if you see lots of times a player bringing
# a +23 Staff, you might consider checking how he did it...)
# 0 to disable
LOGVALUE = 0

# This option permits you to forbid access to the event to the ACCOUNTS listed in it. Do not use capital letters.
# Usefull if you have doubts about player accounts using exploits.
FORBIDDEN_ACCOUNTS = ["test"]

#-------------------- END USER MODIFICATIONS --------------------

qn = "8021_MasterOfEnchanting"

YOGI   = 32599
STAFF  = 13539
SCROLL = 13540
BOX    = 13541
CUBE   = 13542
ADENA  = 57

FIRECRACKER           = [6406]
LFIRECRACKER          = [6407]
SHADOW_HAIR_ACCESSORY = [13074,13075,13076]
EWD                   = [955]
EAD                   = [956]
EWC                   = [951]
EAC                   = [952]
EAB                   = [948]
EWA                   = [729]
HAIR_ACCESSORY        = [13518,13519,13522]
S_ACCESSORY           = [13992]
HIGH_LIFESTONE        = [8752]
EWS                   = [959]
S_ARMOR               = [13991]
S_WEAPON              = [13990]
TOP_LIFESTONE         = [8762]
SOULCRY               = [9570,9571,9572]
S80_ARMOR             = [13989]
S80_WEAPON            = [13988]

REWARDS = [
[[FIRECRACKER,1]],
[[FIRECRACKER,2],[LFIRECRACKER,1]],
[[FIRECRACKER,3],[LFIRECRACKER,2]],
[[SHADOW_HAIR_ACCESSORY,1]],
[[EWD,1]],
[[EWD,1],[EAD,1]],
[[EWC,1]],
[[EWC,1],[EAC,1]],
[[EAB,1]],
[[EWA,1]],
[[HAIR_ACCESSORY,1]],
[[S_ACCESSORY,1]],
[[HIGH_LIFESTONE,1]],
[[EWS,1]],
[[S_ARMOR,1]],
[[S_WEAPON,1]],
[[SOULCRY,1]],
[[TOP_LIFESTONE,1],[HIGH_LIFESTONE,1],[SOULCRY,1]],
[[S80_ARMOR,1]],
[[S80_WEAPON,1]]
]

TEXTS = ["Don't pass up the chance to win an S80 Weapon.","Care to challenge fate and test your luck?"]

if RATEADENA == 0:
    RATE = int(Config.RATE_DROP_ADENA)
else:
    RATE = RATEADENA

def getDate(self):
    calendar = GregorianCalendar()
    date = calendar.getTime()
    dayFormat = SimpleDateFormat("dd")
    monthFormat = SimpleDateFormat("MM")
    yearFormat = SimpleDateFormat("yyyy")
    DAY = int(dayFormat.format(date))
    MONTH = int(monthFormat.format(date))
    YEAR = int(yearFormat.format(date))
    return "%04d%02d%02d" % (YEAR, MONTH, DAY)

def check(self,player) :
    account = player.getAccountName()
    state = self.loadGlobalQuestVar(account)
    currentTime = System.currentTimeMillis() / 1000
    if state == "":
        self.saveGlobalQuestVar(account,str(currentTime))
        return 0
    else:
        if currentTime < int(state) + 21600:
            return int(state)
        else:
            self.saveGlobalQuestVar(account,str(currentTime))
            return 0

def rewardPlayer(self,st,enchant):
    st.takeItems(STAFF, -1)
    if enchant > 23:
        enchant = 23
    rewardList = REWARDS[enchant-4]
    for reward in rewardList:
        itemList = reward[0]
        amount = reward[1]
        item = itemList[Rnd.get(len(itemList))]
        st.giveItems(item, amount)
    return

class Quest (JQuest) :

    def __init__(self,id,name,descr): 
        JQuest.__init__(self,id,name,descr)
        DATE = getDate(self)
        if int(DATE) >= int(EVENTSTARTDATE) and int(DATE) <= int(EVENTENDDATE):
            self.startQuestTimer("SpawnYogi", 30000, None, None)
            print "Master of Enchanting event is ON"
        else:
            print "Master of Enchanting event is OFF"

    def onAdvEvent (self,event,npc,player):
        if event == "Autochat":
            text = TEXTS[Rnd.get(len(TEXTS))]
            npc.broadcastPacket(NpcSay(npc.getObjectId(),0,npc.getNpcId(),str(text)))
            return
        elif event == "SpawnYogi":
            self.yogiSpawnList = []
            yogi = self.addSpawn(YOGI, 82941, 149318, -3473, 39138, False, 0, False, 0)
            self.yogiSpawnList.append(yogi)
            yogi = self.addSpawn(YOGI, 148022, -55481, -2738, 34214, False, 0, False, 0)
            self.yogiSpawnList.append(yogi)
            yogi = self.addSpawn(YOGI, 15776, 142872, -2710, 17423, False, 0, False, 0)
            self.yogiSpawnList.append(yogi)
            yogi = self.addSpawn(YOGI, -14067, 123290, -3121, 18774, False, 0, False, 0)
            self.yogiSpawnList.append(yogi)
            yogi = self.addSpawn(YOGI, 87015, -143229, -1296, 4092, False, 0, False, 0)
            self.yogiSpawnList.append(yogi)
            yogi = self.addSpawn(YOGI, 117104, 77017, -2699, 32767, False, 0, False, 0)
            self.yogiSpawnList.append(yogi)
            yogi = self.addSpawn(YOGI, 146772, 25906, -2017, 57344, False, 0, False, 0)
            self.yogiSpawnList.append(yogi)
            yogi = self.addSpawn(YOGI, 43984, -47713, -801, 47497, False, 0, False, 0)
            self.yogiSpawnList.append(yogi)
            yogi = self.addSpawn(YOGI, 82882, 53098, -1500, 17351, False, 0, False, 0)
            self.yogiSpawnList.append(yogi)
            yogi = self.addSpawn(YOGI, -83127, 150941, -3133, 0, False, 0, False, 0)
            self.yogiSpawnList.append(yogi)
            yogi = self.addSpawn(YOGI, 111333, 219424, -3550, 48247, False, 0, False, 0)
            self.yogiSpawnList.append(yogi)
            if AUTODESPAWN:
                self.startQuestTimer("DespawnYogi", 1800000, None, None)
            return
        elif event == "DespawnYogi":
            DATE = getDate(self)
            if int(DATE) <= int(EVENTENDDATE):
                self.startQuestTimer("DespawnYogi", 1800000, None, None)
            else:
                for yogi in self.yogiSpawnList:
                    yogi.decayMe()
                print "Master of Enchanting event is finished"  
            return
        st = player.getQuestState(qn)
        if not st: st = self.newQuestState(player)
        htmltext = event
        npcId = npc.getNpcId()
        if npcId == YOGI:
            if event == "32599.htm":
                htmltext = st.showHtmlFile("32599.htm").replace("Adena1", str(1000 * RATE))
                htmltext = htmltext.replace("Adena2", str(6000 * RATE))
                htmltext = htmltext.replace("Adena3", str(77777 * RATE))
                htmltext = htmltext.replace("Adena4", str(777770 * RATE))
            elif event == "32599-01.htm":
                if st.getQuestItemsCount(STAFF) >= 1 or st.getQuestItemsCount(ADENA) < 1000 * RATE:
                    htmltext = "32599-02.htm"
                else:
                    st.takeItems(ADENA, 1000 * RATE)
                    st.giveItems(STAFF, 1)
            elif event == "32599-03.htm":
                if st.getQuestItemsCount(ADENA) < 6000 * RATE:
                    htmltext = "32599-02.htm"
                else:
                    lastUsed = check(self,player)
                    if lastUsed == 0:
                        st.takeItems(ADENA, 6000 * RATE)
                        st.giveItems(SCROLL, 24)
                    else:
                        remainingTime = (lastUsed + 21600) - (System.currentTimeMillis() / 1000)
                        player.sendPacket(SystemMessage.sendString("Remaining time: "+str(remainingTime)+" seconds"))
            elif event == "32599-05.htm":
                if st.getQuestItemsCount(ADENA) < 77777 * RATE:
                    htmltext = "32599-02.htm"
                else:
                    st.takeItems(ADENA, 77777 * RATE)
                    st.giveItems(SCROLL, 1)
            elif event == "32599-06.htm":
                if st.getQuestItemsCount(ADENA) < 777770 * RATE:
                    htmltext = "32599-02.htm"
                else:
                    st.takeItems(ADENA, 777770 * RATE)
                    st.giveItems(SCROLL, 10)
            elif event == "Rewards":
                account = player.getAccountName()
                if account in FORBIDDEN_ACCOUNTS:
                    player.sendPacket(SystemMessage.sendString("Your account is not allowed to participate in the event. Contact a GameMaster if you need explanations."))
                    return ""
                weapon = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LRHAND);
                if weapon:
                    weaponId = weapon.getItemId()
                    if weaponId == STAFF and weapon.getEnchantLevel() > 3:
                        enchant = weapon.getEnchantLevel()
                        if LOGVALUE > 0 and enchant >= LOGVALUE:
                            print "Master of Enchanting event: Player "+str(player.getName())+" brought a +"+str(enchant)+" weapon"
                        rewardPlayer(self,st,enchant)
                        htmltext = "32599-08.htm"
                    else:
                        htmltext = "32599-07.htm"
                else:
                    htmltext = "32599-07.htm"
        return htmltext

    def onTalk (self,npc,player):
        htmltext = "<html><body>You are either not on a quest that involves this NPC, or you don't meet this NPC's minimum quest requirements.</body></html>"
        st = player.getQuestState(qn)
        if not st : return htmltext
        npcId = npc.getNpcId()
        if npcId == YOGI :
            account = player.getAccountName()
            if account in FORBIDDEN_ACCOUNTS:
                player.sendPacket(SystemMessage.sendString("Your account is not allowed to participate in the event. Contact a GameMaster if you need explanations."))
                return ""
            if ALLOWEDLEVELS > 0 and player.getLevel() < ALLOWEDLEVELS:
                player.sendPacket(SystemMessage.sendString("Sorry, you may only take part of this event once you reached level "+str(ALLOWEDLEVELS)))
                return ""
            htmltext = st.showHtmlFile("32599.htm").replace("Adena1", str(1000 * RATE))
            htmltext = htmltext.replace("Adena2", str(6000 * RATE))
            htmltext = htmltext.replace("Adena3", str(77777 * RATE))
            htmltext = htmltext.replace("Adena4", str(777770 * RATE))
        return htmltext

    def onSpawn (self,npc):
        self.startQuestTimer("Autochat", 60000, npc, None, True)
        return
        
QUEST = Quest(8021,qn,"official_events")

QUEST.addStartNpc(YOGI)
QUEST.addTalkId(YOGI)
QUEST.addSpawnId(YOGI)