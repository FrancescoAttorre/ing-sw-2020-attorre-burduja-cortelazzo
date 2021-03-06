package it.polimi.ingsw.controller;

import com.sun.jdi.ClassType;
import it.polimi.ingsw.game.*;
import it.polimi.ingsw.network.ICommandReceiver;
import it.polimi.ingsw.network.INetworkAdapter;
import it.polimi.ingsw.network.TPCNetwork;
import it.polimi.ingsw.network.server.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ControllerTest
{
    private static final int SERVER_ID = -11111;

    private Controller controller;
    private INetworkAdapter adapter;
    private List<CommandWrapper> commandHistory;

    @BeforeEach
    void setUp() {
        commandHistory = new ArrayList<>();
        adapter = new INetworkAdapter() {

            @Override
            public void startServer(int port) {

            }

            @Override
            public void stopServer() {

            }

            @Override
            public boolean connect(String ip, int port, String username) {

                return false;
            }

            @Override
            public void disconnect() {

            }

            @Override
            public void setReceiver(ICommandReceiver receiver) {

            }

            @Override
            public void startServer()
            {

            }

            @Override
            public int getDefaultPort()
            {
                return 0;
            }

            @Override
            public void removeReceiver() {

            }

            @Override
            public void send(CommandWrapper packet) {
                commandHistory.add(packet);
            }
            @Override
            public int getClientID(){
                return -1;
            }

            @Override
            public int getServerID() {
                return 0;
            }

            @Override
            public int getBroadCastID() {
                return 0;
            }
        };
        controller = new Controller(adapter);
    }


    // get the last command of the required type
    // form sent command history
    private CommandWrapper getLastHistoryCommandOfType(CommandType ctype)
    {
        for(int i = commandHistory.size()-1; i >= 0; i--)
        {
            if(commandHistory.get(i).getType().equals(ctype))
                return commandHistory.get(i);
        }
        return null;
    }

    //utility method for join command incoming
    private CommandWrapper createJoinCommand(int sender,String username,boolean isJoin){
        JoinCommand cmd = new JoinCommand(sender,SERVER_ID,username,isJoin);
        CommandWrapper wrap = new CommandWrapper(CommandType.JOIN,cmd);
        wrap.Serialize();
        return wrap;
    }

    @Test
    void shouldConnectOnePlayer(){
        assertEquals(0,controller.getConnectedPlayers().size());

        //player 1 connect to game
        controller.onConnect(createJoinCommand(1,"Kekko",true));

        assertEquals(1,controller.getConnectedPlayers().size());
        assertEquals("Kekko",controller.getConnectedPlayers().get(0).getUsername());
        assertEquals(1,controller.getConnectedPlayers().get(0).getId());

        assertEquals(CommandType.JOIN, controller.getLastSent().getType());
        var jcmd = controller.getLastSent().getCommand(JoinCommand.class);
        assertTrue(jcmd.isJoin());
        assertTrue(jcmd.isValidUsername());
        assertEquals(1, jcmd.getHostPlayerID());

    }

    @Test
    void shouldSendAckForSuccessfulJoinOnePlayer(){
        controller.onConnect(createJoinCommand(1,"Kekko",true));
        //player 1 connect to game, and an ack is sent back (true val if success join)
        assertEquals(CommandType.JOIN,controller.getLastSent().getType());

        controller.getLastSent().Serialize();
        JoinCommand cmd = controller.getLastSent().getCommand(JoinCommand.class);

        assertTrue(cmd.isJoin());
    }

    @Test
    void shouldNotConnectOnePlayer(){
        assertEquals(0,controller.getConnectedPlayers().size());
        //player 1 try to connect with wrong content (leave command and username null)
        controller.onConnect(createJoinCommand(1,"Kekko",false));

        assertEquals(0,controller.getConnectedPlayers().size());
        assertEquals(0,controller.getMatch().getPlayers().size());

        controller.onConnect(createJoinCommand(1,null,true));

        assertEquals(0,controller.getConnectedPlayers().size());
        assertEquals(0,controller.getMatch().getPlayers().size());

    }

    @Test
    void shouldSendAckForUnsuccessfulJoinOnePlayer(){
        controller.onConnect(createJoinCommand(1,"Kekko",false));
        //player try to connect with wrong content : ack join with false bool
        assertEquals(CommandType.JOIN,controller.getLastSent().getType());
        controller.getLastSent().Serialize();
        JoinCommand cmd = controller.getLastSent().getCommand(JoinCommand.class);
        assertFalse(cmd.isJoin());
        //player 1 try to connect without usernaem : ack join with false bool
        controller.onConnect(createJoinCommand(2,null,true));
        assertEquals(CommandType.JOIN,controller.getLastSent().getType());
        controller.getLastSent().Serialize();
        cmd = controller.getLastSent().getCommand(JoinCommand.class);
        assertFalse(cmd.isJoin());
    }

    @Test
    void shouldSendAckForUnsuccessfulJoinBecauseOfGameRefuse(){
        join3Player();
        //3 player already joined, player 4 try to connect (game return false to this attempt)
        controller.onConnect(createJoinCommand(4,"Kekko",true));
        //is sent back an ack to the player 4
        assertEquals(CommandType.JOIN,controller.getLastSent().getType());
        controller.getLastSent().Serialize();
        JoinCommand cmd = controller.getLastSent().getCommand(JoinCommand.class);
        assertFalse(cmd.isJoin());
    }

    @Test
    void shouldNotConnect2PlayerWithSameUsername(){
        controller.onConnect(createJoinCommand(1,"Kekko",true));
        //player 2 try to join game with a username already taken
        controller.onConnect(createJoinCommand(2,"Kekko",true));
        //player 2 can't connect
        assertEquals(1,controller.getConnectedPlayers().size());
        assertEquals(1,controller.getMatch().getPlayers().size());
    }

    @Test
    void shouldSendAckForUnsuccessfulJoinSameUsername(){
        controller.onConnect(createJoinCommand(1,"Kekko",true));
        controller.onConnect(createJoinCommand(2,"Kekko",true));
        //player 2 can't connect to game because of same username as player 1, an ack with false val is sent
        assertEquals(CommandType.JOIN,controller.getLastSent().getType());
        controller.getLastSent().Serialize();
        JoinCommand cmd = controller.getLastSent().getCommand(JoinCommand.class);
        assertFalse(cmd.isJoin());

    }

    @Test
    void shouldConnect2PlayerAndWaitStart(){
        controller.onConnect(createJoinCommand(1,"Kekko",true));
        //player 2 connect to game
        controller.onConnect(createJoinCommand(2,"Vlad",true));

        assertEquals(2,controller.getConnectedPlayers().size());
        assertEquals("Vlad",controller.getConnectedPlayers().get(1).getUsername());
        assertEquals(2,controller.getConnectedPlayers().get(1).getId());
        //a game not start until a start command is sent by host
        assertFalse(controller.getMatch().isStarted());
        assertEquals(CommandType.JOIN, controller.getLastSent().getType());

        //check host is still player 1
        assertEquals(1, controller.getLastSent().getCommand(JoinCommand.class).getHostPlayerID());
    }

    //phase helper method
    private void join3Player(){
        controller.onConnect(createJoinCommand(1,"Kekko",true));
        controller.onConnect(createJoinCommand(2,"Vlad",true));
        controller.onConnect(createJoinCommand(3,"Ricky",true));
    }

    //phase helper method
    private void join2Player(){
        controller.onConnect(createJoinCommand(1,"Kekko",true));
        controller.onConnect(createJoinCommand(2,"Vlad",true));
    }

    //utility method for start command incoming
    private CommandWrapper createStartCommand(int sender){
        return StartCommand.makeRequest(sender, SERVER_ID);
    }


    @Test
    void commandNotExpectedAfterJoin2Player(){
        join2Player();

        CommandWrapper exCmd = controller.getLastSent();
        //player 1 create a filter command
        controller.onCommand(createFilterGodCommand(1,new int[]{1,2}));
        //game is not started and command is discarded
        assertEquals(exCmd,controller.getLastSent());
    }

    @Test
    void commandNotExpectedAfterStart3Player(){
        join3Player();
        CommandWrapper exCmd = controller.getLastSent();
        //player 1 pick god, it is expected a filter god command
        controller.onCommand(createPickGodCommand(1,1));

        assertEquals(exCmd,controller.getLastSent());

    }

    @Test
    void shouldStartGameWith2Player(){
        controller.onConnect(createJoinCommand(1,"Kekko",true));
        controller.onConnect(createJoinCommand(2,"Vlad",true));
        //player 1 start a game
        controller.onCommand(createStartCommand(1));
        //player 1 is host, game is started
        assertTrue(controller.getMatch().isStarted());
        assertEquals(CommandType.FILTER_GODS,controller.getLastSent().getType());
    }

    @Test
    void shouldNotStartGameFromNonHost(){
        controller.onConnect(createJoinCommand(1,"Kekko",true));
        controller.onConnect(createJoinCommand(2,"Vlad",true));
        //player 2 try to start game
        controller.onCommand(createStartCommand(2));
        //player 2 is not host, game is not started
        assertFalse(controller.getMatch().isStarted());
        assertEquals(CommandType.JOIN,controller.getLastSent().getType());
    }

    @Test
    void shouldNotStartGameWithOnePlayer(){
        controller.onConnect(createJoinCommand(1,"Kekko",true));
        //player 1 try to start game
        controller.onCommand(createStartCommand(1));
        //only one player is connected, can't start game
        assertFalse(controller.getMatch().isStarted());
        assertEquals(CommandType.JOIN,controller.getLastSent().getType());
    }

    @Test
    void shouldConnect3PlayerAndStartGame(){
        join3Player();
        //connect 3 player and automatically start a game
        assertEquals(3,controller.getConnectedPlayers().size());
        //next request is filter god
        assertTrue(controller.getMatch().isStarted());
        assertEquals(CommandType.FILTER_GODS,controller.getLastSent().getType());
    }

    @Test
    void shouldTrashStartRequestInStartedGame(){
        join3Player();
        CommandWrapper exCmd = controller.getLastSent();
        //start game command arrive in an already started game
        controller.onCommand(createStartCommand(1));

        assertEquals(exCmd,controller.getLastSent());
    }

    @Test
    void shouldNotAcceptMoreThan3Players(){
        join3Player();
        //player 4 try to connect to game
        controller.onConnect(createJoinCommand(4,"CrazyFourthPlayer",true));

        assertEquals(3,controller.getConnectedPlayers().size());
        assertEquals(CommandType.JOIN,controller.getLastSent().getType());
    }

    //utility method for filter command
    private CommandWrapper createFilterGodCommand(int sender,int[] ids){
        FilterGodCommand cmd = new FilterGodCommand(sender,SERVER_ID,ids);
        CommandWrapper wrapper = new CommandWrapper(CommandType.FILTER_GODS,cmd);
        wrapper.Serialize();
        return wrapper;
    }

    //phase helper method
    private void startGame2Player (){
        controller.onCommand(createStartCommand(1));
    }

    @Test
    void shouldSendFilterGodCommandToHost(){
        join3Player();
        //started a 3 player game
        controller.getLastSent().Serialize();
        FilterGodCommand cmd = controller.getLastSent().getCommand(FilterGodCommand.class);
        //a filter god command is sent back to host
        assertEquals(CommandType.FILTER_GODS,controller.getLastSent().getType());
        assertEquals(1,cmd.getTarget());
    }

    @Test
    void shouldFilterGod(){
        join3Player();
        //player 1 choose 3 god to be filtered
        controller.onCommand(createFilterGodCommand(1,new int[]{1,2,3}));

        assertEquals(Game.GameState.GOD_PICK,controller.getMatch().getCurrentState());
        assertEquals(CommandType.PICK_GOD,controller.getLastSent().getType());

        controller.getLastSent().Serialize();
        PickGodCommand cmd = controller.getLastSent().getCommand(PickGodCommand.class);
        assertEquals(3,cmd.getAllowedGodsIDS().length);
    }

    @Test
    void shouldNotFilterGod(){
        join3Player();
        //player 2 try to filter god
        controller.onCommand(createFilterGodCommand(2,new int[]{1,2,3}));
        //player 2 is not host, can't filter god
        assertEquals(Game.GameState.GOD_FILTER,controller.getMatch().getCurrentState());
        assertEquals(CommandType.FILTER_GODS,controller.getLastSent().getType());
        //player 1 filter god, but select only 2 id
        controller.onCommand(createFilterGodCommand(1,new int[]{1,2}));
        assertEquals(Game.GameState.GOD_FILTER,controller.getMatch().getCurrentState());
        assertEquals(CommandType.FILTER_GODS,controller.getLastSent().getType());
        //player 1 filter god, but select 4 id
        controller.onCommand(createFilterGodCommand(1,new int[]{1,2,3,4}));
        assertEquals(Game.GameState.GOD_FILTER,controller.getMatch().getCurrentState());
        assertEquals(CommandType.FILTER_GODS,controller.getLastSent().getType());

    }

    @Test
    void commandNotExpectedAfterFilterGod(){
        join3Player();
        filterGod3Player();
        CommandWrapper exCmd = controller.getLastSent();

        controller.onCommand(createFirstPlayerPickCommand(1,2));

        assertEquals(exCmd,controller.getLastSent());
    }

    //phase helper method
    private void filterGod3Player(){
        controller.onCommand(createFilterGodCommand(1,new int[]{1,2,3}));
    }

    //phase helper method
    private void filterGod2Player(){
        controller.onCommand(createFilterGodCommand(1,new int[]{1,2}));
    }

    //utility method incoming
    private CommandWrapper createPickGodCommand(int sender,int god){
        PickGodCommand cmd = new PickGodCommand(sender,SERVER_ID,god);
        CommandWrapper wrapper = new CommandWrapper(CommandType.PICK_GOD,cmd);
        wrapper.Serialize();
        return wrapper;
    }

    @Test
    void shouldPickGodPlayerAfterHostHasFilterGods(){
        join3Player();
        filterGod3Player();
        //player 2 pick god 2
        controller.onCommand(createPickGodCommand(2,2));
        //player 2 is first player after host in order
        assertEquals(2,controller.getConnectedPlayers().get(1).getGod().getId());

        controller.getLastSent().Serialize();
        PickGodCommand cmd = controller.getLastSent().getCommand(PickGodCommand.class);
        assertEquals(3,cmd.getTarget());
        assertEquals(2,cmd.getAllowedGodsIDS().length);
    }

    @Test
    void shouldNotPickGodNotYourTurn(){
        join3Player();
        filterGod3Player();
        CommandWrapper exCmd = controller.getLastSent();
        //player 1 pick god 1
        controller.onCommand(createPickGodCommand(1,1));
        //player 1 is not first player after host in order, can't pick now
        assertEquals(exCmd,controller.getLastSent());
    }

    @Test
    void shouldNotPickGodNotAvailable(){
        join3Player();
        filterGod3Player();
        CommandWrapper exCmd = controller.getLastSent();
        //player 2 try to pick a not available god
        controller.onCommand(createPickGodCommand(2,4));

        assertEquals(exCmd.toString(),controller.getLastSent().toString());
    }

    @Test
    void shouldPickGod3PlayerAndGetToFirstPlayerSelection(){
        join3Player();
        filterGod3Player();
        controller.onCommand(createPickGodCommand(2,2));
        //player 1 and player 2 pick god
        controller.onCommand(createPickGodCommand(3,3));
        assertEquals(3,controller.getConnectedPlayers().get(2).getGod().getId());
        //automatically assigned last god to host
        assertEquals(CommandType.SELECT_FIRST_PLAYER,controller.getLastSent().getType());
        assertEquals(1,controller.getConnectedPlayers().get(0).getGod().getId());
        //correct assignment check
        controller.getLastSent().Serialize();
        FirstPlayerPickCommand cmd = controller.getLastSent().getCommand(FirstPlayerPickCommand.class);
        assertEquals(1,cmd.getTarget());
        assertEquals(3,cmd.getPlayers().length);

    }

    @Test
    void shouldPickGod2PlayerAndGetToFirstPlayerSelection(){
        join2Player();
        startGame2Player();
        filterGod2Player();
        controller.onCommand(createPickGodCommand(2,2));
        assertEquals(2,controller.getConnectedPlayers().get(1).getGod().getId());
        //player 2 select god 2, other god is automatically assigned to host
        assertEquals(1,controller.getConnectedPlayers().get(0).getGod().getId());
        //go to next phase
        assertEquals(CommandType.SELECT_FIRST_PLAYER,controller.getLastSent().getType());
    }

    @Test
    void commandNotExpectedInPickGodPhase(){
        join3Player();
        filterGod3Player();

        //player 2 select a god, it's player 3 turn to chose
        controller.onCommand(createPickGodCommand(2,2));
        CommandWrapper exCmd = controller.getLastSent();
        //arrive the first player selection from host
        controller.onCommand(createFirstPlayerPickCommand(1,1));

        assertEquals(exCmd,controller.getLastSent());
    }

    //phase helper method
    private void pickGod3Player(){
        controller.onCommand(createPickGodCommand(2,2));
        controller.onCommand(createPickGodCommand(3,3));
        //last god is automatically assigned to host
    }

    //phase helper method
    private void pickGod2Player(){
        controller.onCommand(createPickGodCommand(2,2));
        //last god is automatically assigned to host
    }

    //utility method for first player command incoming
    private CommandWrapper createFirstPlayerPickCommand(int sender,int firstPlayer){
        FirstPlayerPickCommand cmd = new FirstPlayerPickCommand(sender, SERVER_ID,firstPlayer);
        CommandWrapper wrapper = new CommandWrapper(CommandType.SELECT_FIRST_PLAYER,cmd);
        wrapper.Serialize();
        return wrapper;
    }

    @Test
    void shouldSendFirstPlayerSelectionToHost(){
        join3Player();
        filterGod3Player();
        pickGod3Player();
        //every player select a god, a first player selection is sent to host
        controller.getLastSent().Serialize();
        FirstPlayerPickCommand cmd = controller.getLastSent().getCommand(FirstPlayerPickCommand.class);
        assertEquals(1,cmd.getTarget());
        assertEquals(3,cmd.getPlayers().length);
    }

    @Test
    void shouldSelectFirstPlayer3Players(){
        join3Player();
        filterGod3Player();
        pickGod3Player();
        //player 1 select a first player for this game
        controller.onCommand(createFirstPlayerPickCommand(1,2));

        assertEquals(2,controller.getMatch().getCurrentPlayer().getId());
        assertEquals(CommandType.PLACE_WORKERS,controller.getLastSent().getType());
    }

    @Test
    void shouldSelectFirstPlayer2Players(){
        join2Player();
        startGame2Player();
        filterGod2Player();
        pickGod2Player();
        //player 1 select a first player for this game
        controller.onCommand(createFirstPlayerPickCommand(1,2));

        assertEquals(2,controller.getMatch().getCurrentPlayer().getId());
        assertEquals(CommandType.PLACE_WORKERS,controller.getLastSent().getType());
    }

    @Test
    void shouldNotSelectFirstPlayerANonHostPlayer(){
        join3Player();
        filterGod3Player();
        pickGod3Player();
        //player 2 try to select a first player for this game
        controller.onCommand(createFirstPlayerPickCommand(2,2));
        //player 2 is not host
        assertEquals(CommandType.SELECT_FIRST_PLAYER,controller.getLastSent().getType());
    }

    @Test
    void shouldNotSelectFirstPlayerIfNotExist(){
        join2Player();
        startGame2Player();
        filterGod2Player();
        pickGod2Player();
        //player 1 try to chose a player that has not joined this game
        controller.onCommand(createFirstPlayerPickCommand(1,3));

        assertEquals(CommandType.SELECT_FIRST_PLAYER,controller.getLastSent().getType());
    }

    @Test
    void commandNotExpectedInFirstPlayerSelectionPhase(){
        join3Player();
        filterGod3Player();
        pickGod3Player();

        CommandWrapper exCmd = controller.getLastSent();
        //a first player selection is expected, arrive a worker place command for the current player (host)
        controller.onCommand(createWorkerPlaceCommand(1,new Vector2[]{new Vector2(0,0),new Vector2(0,1)}));
        //impossible action because of a first player selection is needed
        assertEquals(exCmd,controller.getLastSent());
    }

    //phase helper method
    private void selectFirstPlayer(){
        controller.onCommand(createFirstPlayerPickCommand(1,2));
    }

    //utility method for Worker Place Command
    private CommandWrapper createWorkerPlaceCommand(int sender,Vector2[] pos){
        WorkerPlaceCommand cmd = new WorkerPlaceCommand(sender,SERVER_ID,pos);
        CommandWrapper wrapper = new CommandWrapper(CommandType.PLACE_WORKERS,cmd);
        wrapper.Serialize();
        return wrapper;
    }

    @Test
    void shouldSendPossiblePosition(){
        join3Player();
        filterGod3Player();
        pickGod3Player();
        selectFirstPlayer();
        //first worker place command is sent to client with every position of the map
        controller.getLastSent().Serialize();
        WorkerPlaceCommand cmd = controller.getLastSent().getCommand(WorkerPlaceCommand.class);
        assertEquals(Map.HEIGHT * Map.LENGTH, cmd.getPositions().length);
        //sent to the correct first player chosen
        assertEquals(2,cmd.getTarget());
    }

    @Test
    void shouldPlace2Worker(){
        join3Player();
        filterGod3Player();
        pickGod3Player();
        selectFirstPlayer();

        controller.getLastSent().Serialize();
        WorkerPlaceCommand cmd = controller.getLastSent().getCommand(WorkerPlaceCommand.class);
        Vector2[] availablePos = new Vector2[2];
        availablePos[0] = cmd.getPositions()[0];
        availablePos[1] = cmd.getPositions()[1];
        //client select 2 possible worker position from the command received
        controller.onCommand(createWorkerPlaceCommand(2,availablePos));

        controller.getLastSent().Serialize();
        cmd = controller.getLastSent().getCommand(WorkerPlaceCommand.class);
        assertEquals(3,cmd.getTarget());
        assertEquals((Map.LENGTH * Map.HEIGHT)-2,cmd.getPositions().length);
        //correct value of possible positions are updated, and command is sent to next player
        assertFalse(controller.getMatch().getCurrentMap().isCellEmpty(new Vector2(0,0)));
        assertFalse(controller.getMatch().getCurrentMap().isCellEmpty(availablePos[1]));
    }

    @Test
    void shouldNotPlaceWorkerInNotAvailablePosition(){
        join3Player();
        filterGod3Player();
        pickGod3Player();
        selectFirstPlayer();
        //player 2 place a worker in (0,0)
        controller.onCommand(createWorkerPlaceCommand(2,new Vector2[]{new Vector2(0,0),new Vector2(0,1)}));
        //player 3 try to place a worker in (0,0)
        controller.onCommand(createWorkerPlaceCommand(3,new Vector2[]{new Vector2(0,0),new Vector2(3,3)}));

        controller.getLastSent().Serialize();
        WorkerPlaceCommand cmd = controller.getLastSent().getCommand(WorkerPlaceCommand.class);
        //player 3 has to repeat the entire worker placement
        assertEquals(3,cmd.getTarget());
        assertEquals((Map.LENGTH * Map.HEIGHT)-2,cmd.getPositions().length);
        //player 3 selection are both discarded
        assertFalse(controller.getMatch().getCurrentMap().isCellEmpty(new Vector2(0,0)));
        assertTrue(controller.getMatch().getCurrentMap().isCellEmpty(new Vector2(3,3)));
        assertEquals(2,controller.getMatch().getCurrentMap().getWorker(new Vector2(0,0)).getOwner().getId());
    }

    @Test
    void shouldNotPlaceOnlyOneWorker(){
        join3Player();
        filterGod3Player();
        pickGod3Player();
        selectFirstPlayer();
        //player 2 try to place only one worker
        controller.onCommand(createWorkerPlaceCommand(2,new Vector2[]{new Vector2(0,0)}));

        controller.getLastSent().Serialize();
        WorkerPlaceCommand cmd = controller.getLastSent().getCommand(WorkerPlaceCommand.class);

        assertEquals(Map.LENGTH * Map.HEIGHT,cmd.getPositions().length);
        assertTrue(controller.getMatch().getCurrentMap().isCellEmpty(new Vector2(0,0)));

    }

    @Test
    void shouldPlaceWorkers2PlayersAndGetToActionPhase(){
        join2Player();
        startGame2Player();
        filterGod2Player();
        pickGod2Player();
        selectFirstPlayer();
        //player 2 and player 1 place correctly their workers
        controller.onCommand(createWorkerPlaceCommand(2,new Vector2[]{new Vector2(0,0),new Vector2(0,1)}));
        controller.onCommand(createWorkerPlaceCommand(1,new Vector2[]{new Vector2(0,2),new Vector2(0,3)}));
        //get to action phase for first player
        assertEquals(CommandType.ACTION_TIME,controller.getLastSent().getType());
        controller.getLastSent().Serialize();
        ActionCommand cmd = controller.getLastSent().getCommand(ActionCommand.class);
        assertEquals(2,cmd.getTarget());
    }

    @Test
    void shouldPlaceWorkers3PlayersAndGetToActionPhase(){
        join3Player();
        filterGod3Player();
        pickGod3Player();
        selectFirstPlayer();
        //3 players correctly place their workers
        controller.onCommand(createWorkerPlaceCommand(2,new Vector2[]{new Vector2(0,0),new Vector2(0,1)}));
        controller.onCommand(createWorkerPlaceCommand(3,new Vector2[]{new Vector2(0,2),new Vector2(0,3)}));
        controller.onCommand(createWorkerPlaceCommand(1,new Vector2[]{new Vector2(1,1),new Vector2(1,2)}));
        //get to action phase for first player
        assertEquals(CommandType.ACTION_TIME,controller.getLastSent().getType());
        controller.getLastSent().Serialize();
        ActionCommand cmd = controller.getLastSent().getCommand(ActionCommand.class);
        assertEquals(2,cmd.getTarget());
    }


    //utility method for Action Command
    private CommandWrapper createActionCommand(int sender,int worker, int action,Vector2 pos){
        return ActionCommand.makeReply(sender, SERVER_ID, action, worker, pos );
    }

    @Test
    void commandNotExpectedInPlaceWorkerPhase(){
        join3Player();
        filterGod3Player();
        pickGod3Player();
        selectFirstPlayer();
        //player 2 place workers
        controller.onCommand(createWorkerPlaceCommand(2,new Vector2[]{new Vector2(0,0),new Vector2(0,1)}));

        CommandWrapper exCmd = controller.getLastSent();
        //player 1 try to execute an action instead of place its workers
        controller.onCommand(createActionCommand(1,0,0,new Vector2(1,1)));

        assertEquals(exCmd,controller.getLastSent());
        assertEquals(CommandType.PLACE_WORKERS,controller.getLastSent().getType());
    }


    //phase helper method
    private void placeWorker2Player(){
        controller.getLastSent().Serialize();
        WorkerPlaceCommand cmd = controller.getLastSent().getCommand(WorkerPlaceCommand.class);
        controller.onCommand(createWorkerPlaceCommand(2,new Vector2[]{cmd.getPositions()[0],cmd.getPositions()[1]}));

        controller.getLastSent().Serialize();
        cmd = controller.getLastSent().getCommand(WorkerPlaceCommand.class);
        controller.onCommand(createWorkerPlaceCommand(1,new Vector2[]{cmd.getPositions()[0],cmd.getPositions()[1]}));
    }

    //phase helper method
    private void placeWorker3Player(){
        controller.getLastSent().Serialize();
        WorkerPlaceCommand cmd = controller.getLastSent().getCommand(WorkerPlaceCommand.class);
        controller.onCommand(createWorkerPlaceCommand(2,new Vector2[]{cmd.getPositions()[0],cmd.getPositions()[1]}));

        controller.getLastSent().Serialize();
        cmd = controller.getLastSent().getCommand(WorkerPlaceCommand.class);
        controller.onCommand(createWorkerPlaceCommand(3,new Vector2[]{cmd.getPositions()[0],cmd.getPositions()[1]}));

        controller.getLastSent().Serialize();
        cmd = controller.getLastSent().getCommand(WorkerPlaceCommand.class);
        controller.onCommand(createWorkerPlaceCommand(1,new Vector2[]{cmd.getPositions()[0],cmd.getPositions()[1]}));
    }

    /* 3 players
        | 2 | 2 | 3 | 3 |   |
        | 1 | 1 |   |   |   |
        |   |   |   |   |   |
        |   |   |   |   |   |
        |   |   |   |   |   |
     */

    /* 2 players
        | 2 | 2 | 1 | 1 |   |
        |   |   |   |   |   |
        |   |   |   |   |   |
        |   |   |   |   |   |
        |   |   |   |   |   |
     */

    @Test
    void shouldReceiveCorrectNextActions(){
        join3Player();
        filterGod3Player();
        pickGod3Player();
        selectFirstPlayer();
        placeWorker3Player();

        assertEquals(CommandType.ACTION_TIME,controller.getLastSent().getType());

        controller.getLastSent().Serialize();
        ActionCommand cmd = controller.getLastSent().getCommand(ActionCommand.class);

        //2 player can move with 2 worker in 2 and 3 possible cell (total of 5 available cell)
        assertEquals(1,cmd.getAvailableActions()[0].getAvailablePositions().size());
        assertEquals(2,cmd.getAvailableActions()[1].getAvailablePositions().size());

        assertEquals(new Vector2(1,1), cmd.getAvailableActions()[0].getAvailablePositions().get(0));
        assertEquals(new Vector2(1,1), cmd.getAvailableActions()[1].getAvailablePositions().get(0));
        assertEquals(new Vector2(1,2), cmd.getAvailableActions()[1].getAvailablePositions().get(1));

    }

    @Test
    void shouldReceiveCorrectNextActionWithConstraints(){
         join2Player();
         startGame2Player();
         filterGod2Player();
         pickGod2Player();
         selectFirstPlayer();
         placeWorker2Player();

        controller.getLastSent().Serialize();
        ActionCommand cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //2 execute first move action with worker 0 to (1,0)
        controller.onCommand(createActionCommand(2,0,0, cmd.getAvailableActions()[0].getAvailablePositions().get(0)));

        controller.getLastSent().Serialize();
        cmd = controller.getLastSent().getCommand(ActionCommand.class);

        //player 2 has a build action possibilities to 4 cell + 1 undo
        //but has god ability with constraint that lock a cell (total of 7 possible cell)
        assertEquals(3, cmd.getAvailableActions().length);
        assertEquals(4, cmd.getAvailableActions()[0].getAvailablePositions().size());
        assertEquals(3, cmd.getAvailableActions()[1].getAvailablePositions().size());

        assertFalse(controller.getMatch().getCurrentMap().isCellEmpty(new Vector2(0,1)));
        //4 cell available including (0,0)
        assertEquals(new Vector2(0,0), cmd.getAvailableActions()[0].getAvailablePositions().get(0));
        //5th available cell is not (0,0) for god ability application, so the next is (1,1)
        assertEquals(new Vector2(1,1), cmd.getAvailableActions()[1].getAvailablePositions().get(0));
    }

    @Test
    void shouldExecuteAction(){
        join2Player();
        startGame2Player();
        filterGod2Player();
        pickGod2Player();
        selectFirstPlayer();
        placeWorker2Player();

        controller.getLastSent().Serialize();
        ActionCommand cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //player 2 execute a move action in first available cell with first worker (worker 0 in cell (1,0) )
        controller.onCommand(createActionCommand(2,0,0,cmd.getAvailableActions()[0].getAvailablePositions().get(0)));

        assertEquals(1,controller.getMatch().getPlayers().get(1).getWorkers().get(0).getPosition().getX());
        assertEquals(0,controller.getMatch().getPlayers().get(1).getWorkers().get(0).getPosition().getY());
        assertTrue(controller.getMatch().getCurrentMap().isCellEmpty(new Vector2(0,0)));

        controller.getLastSent().Serialize();
        cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //check if worker has been moved and next actions are correct(containing cell just released)
        assertEquals(2,cmd.getTarget());
        assertEquals(new Vector2(0,0), cmd.getAvailableActions()[0].getAvailablePositions().get(0));
    }

    @Test
    void shouldNotExecuteActionWithNotAvailablePosition(){
        join2Player();
        startGame2Player();
        filterGod2Player();
        pickGod2Player();
        selectFirstPlayer();
        placeWorker2Player();

        controller.getLastSent().Serialize();
        CommandWrapper exCmd = controller.getLastSent();
        ActionCommand cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //player 2 execute a move action, but select a cell not available
        controller.onCommand(createActionCommand(2,0,0,new Vector2(0,1)));

        assertEquals(exCmd.toString(),controller.getLastSent().toString());
        assertFalse(controller.getMatch().getCurrentMap().isCellEmpty(new Vector2(0,0)));
    }

    @Test
    void shouldNotExecuteActionWithNotAvailableWorker(){
        join2Player();
        startGame2Player();
        filterGod2Player();
        pickGod2Player();
        selectFirstPlayer();
        placeWorker2Player();

        controller.getLastSent().Serialize();
        CommandWrapper exCmd = controller.getLastSent();
        ActionCommand cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //player 2 execute a move action, but select a impossible worker id
        controller.onCommand(createActionCommand(2,3,0,cmd.getAvailableActions()[0].getAvailablePositions().get(0)));

        assertEquals(exCmd.toString(),controller.getLastSent().toString());
        assertFalse(controller.getMatch().getCurrentMap().isCellEmpty(new Vector2(0,0)));
    }

    @Test
    void shouldNotExecuteActionWithNotAvailablePositionId(){
        join2Player();
        startGame2Player();
        filterGod2Player();
        pickGod2Player();
        selectFirstPlayer();
        placeWorker2Player();

        controller.getLastSent().Serialize();
        CommandWrapper exCmd = controller.getLastSent();
        ActionCommand cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //player 2 execute a move action, but select a impossible action id
        controller.onCommand(createActionCommand(2,0,3, cmd.getAvailableActions()[0].getAvailablePositions().get(0)));

        assertEquals(exCmd.toString(),controller.getLastSent().toString());
        assertFalse(controller.getMatch().getCurrentMap().isCellEmpty(new Vector2(0,0)));
    }

    @Test
    void shouldNotChangeWorker(){
        join2Player();
        startGame2Player();
        filterGod2Player();
        pickGod2Player();
        selectFirstPlayer();
        placeWorker2Player();

        controller.getLastSent().Serialize();
        ActionCommand cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //player 2 execute a move action in first available cell with first worker (worker 0 in cell (0,1) )
        controller.onCommand(createActionCommand(2,0,0, new Vector2(0,1) ));

        CommandWrapper exCmd = controller.getLastSent();
        //player 2 try to execute next possible action with a different worker
        controller.onCommand(createActionCommand(2,0,1,cmd.getAvailableActions()[0].getAvailablePositions().get(0)));

        assertEquals(exCmd.toString(),controller.getLastSent().toString());
    }

    @Test
    void shouldSendMoreActionPossibilities(){
        join2Player();
        startGame2Player();
        filterGod2Player();
        pickGod2Player();
        selectFirstPlayer();
        placeWorker2Player();

        controller.getLastSent().Serialize();
        ActionCommand cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //player 2 execute a move action in first available cell with first worker (worker 0 in cell (0,1) )
        controller.onCommand(createActionCommand(2,0,0,cmd.getAvailableActions()[0].getAvailablePositions().get(0)));

        controller.getLastSent().Serialize();
        cmd = controller.getLastSent().getCommand(ActionCommand.class);
        assertEquals(3,cmd.getAvailableActions().length);
    }

    @Test
    void shouldExecuteOneOfMorePossibleAction(){
        join2Player();
        startGame2Player();
        filterGod2Player();
        pickGod2Player();
        selectFirstPlayer();
        placeWorker2Player();

        controller.getLastSent().Serialize();
        ActionCommand cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //player 2 execute a move action in first available cell with first worker (worker 0 in cell (0,1) )
        controller.onCommand(createActionCommand(2,0,0, cmd.getAvailableActions()[0].getAvailablePositions().get(0)));

        controller.getLastSent().Serialize();
        cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //player 2 execute a build action in first available cell with first worker (worker 0 in cell (0,0) )
        controller.onCommand(createActionCommand(2,0,0,cmd.getAvailableActions()[0].getAvailablePositions().get(0)));

        assertEquals(1,controller.getMatch().getCurrentMap().getLevel(new Vector2(0,0)));
    }

    @Test
    void shouldEndTurnAndGetToNextPlayer(){
        join2Player();
        startGame2Player();
        filterGod2Player();
        pickGod2Player();
        selectFirstPlayer();
        placeWorker2Player();
        controller.getLastSent().Serialize();
        ActionCommand cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //player 2 execute a move action in first available cell with first worker (worker 0 in cell (0,1) )
        controller.onCommand(createActionCommand(2,0,0,cmd.getAvailableActions()[0].getAvailablePositions().get(0)));
        controller.getLastSent().Serialize();
        cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //player 2 execute a build action in first available cell with first worker (worker 0 in cell (0,0) )
        controller.onCommand(createActionCommand(2,0,0,cmd.getAvailableActions()[0].getAvailablePositions().get(0)));

        controller.getLastSent().Serialize();
        cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //player 2 execute a end turn action (standard value for end turn action are selected)
        controller.onCommand(createActionCommand(2,0,0,cmd.getAvailableActions()[0].getAvailablePositions().get(0)));

        controller.getLastSent().Serialize();
        cmd = controller.getLastSent().getCommand(ActionCommand.class);
        assertEquals(1,cmd.getTarget());
        assertEquals(CommandType.ACTION_TIME,controller.getLastSent().getType());
    }

    @Test
    void shouldApplyGodPower(){
        join2Player();
        startGame2Player();
        filterGod2Player();
        pickGod2Player();
        selectFirstPlayer();
        placeWorker2Player();
        controller.getLastSent().Serialize();
        ActionCommand cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //player 2 execute a move action in first available cell with first worker (worker 0 in cell (0,1) )
        controller.onCommand(createActionCommand(2,0,0,cmd.getAvailableActions()[0].getAvailablePositions().get(0)));
        controller.getLastSent().Serialize();
        cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //player 2 execute a build action in first available cell with first worker (worker 0 in cell (0,0) )
        controller.onCommand(createActionCommand(2,0,0,cmd.getAvailableActions()[0].getAvailablePositions().get(0)));
        controller.getLastSent().Serialize();
        cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //player 2 execute a end turn action (standard value for end turn action are selected)
        controller.onCommand(createActionCommand(2,0,0,cmd.getAvailableActions()[0].getAvailablePositions().get(0)));

        controller.getLastSent().Serialize();
        cmd = controller.getLastSent().getCommand(ActionCommand.class);
        //player 1 apply his power, can move to cell (0,1)
        controller.onCommand(createActionCommand(1,0,0,cmd.getAvailableActions()[0].getAvailablePositions().get(0)));

        assertEquals(1,controller.getMatch().getCurrentMap().getWorker(new Vector2(0,1)).getOwner().getId());
        assertEquals(2,controller.getMatch().getCurrentMap().getWorker(new Vector2(0,2)).getOwner().getId());
    }

    @Test
    void shouldNotExecuteActionFromNotCurrentPlayer(){
        join3Player();
        filterGod3Player();
        pickGod3Player();
        selectFirstPlayer();
        placeWorker3Player();

        CommandWrapper exCmd = controller.getLastSent();
        //player 3 try to execute a correct action, but it's not his turn
        controller.onCommand(createActionCommand(3,0,0,new Vector2(1,1)));

        assertEquals(exCmd, controller.getLastSent());
    }

    private CommandWrapper createLeaveCommand(int sender){
        return LeaveCommand.makeRequest(sender, SERVER_ID);
    }

    @Test
    void shouldDisconnectPlayer(){
        join3Player();
        filterGod3Player();
        pickGod3Player();
        selectFirstPlayer();
        placeWorker3Player();

        //player 2 disconnect from game
        controller.onDisconnect(createLeaveCommand(2));

        // game is killed when a player disconnects
        assertTrue(controller.getMatch().isEnded());
        controller.getLastSent().Serialize();
        EndGameCommand cmd = controller.getLastSent().getCommand(EndGameCommand.class);
        assertEquals(adapter.getBroadCastID(), cmd.getTarget());
        assertEquals(EndGameCommand.INTERRUPTED_GAME, cmd.getWinnerID());
    }

    @Test
    void shouldDisconnectPlayerFrom2PlayersGame(){
        join2Player();
        startGame2Player();
        filterGod2Player();

        //player 2 disconnect from game
        controller.onDisconnect(createLeaveCommand(2));

        //player 1 win the game
        assertEquals(CommandType.END_GAME,controller.getLastSent().getType());

        controller.getLastSent().Serialize();
        EndGameCommand cmd = controller.getLastSent().getCommand(EndGameCommand.class);
        assertEquals(adapter.getBroadCastID() , cmd.getTarget());
        assertEquals(EndGameCommand.INTERRUPTED_GAME, cmd.getWinnerID());

        assertTrue(controller.getMatch().isEnded());
    }

    //TODO : win condition from an ended game test

    @Test
    void shouldDetectPlayerLostDuringMatch()
    {
        // join with 3 players
        controller.onConnect(JoinCommand.makeRequest(1, SERVER_ID, "Gompachiro"));

        controller.onConnect(JoinCommand.makeRequest(2, SERVER_ID, "Nezuko"));
        controller.onConnect(JoinCommand.makeRequest(3, SERVER_ID, "Inosuke"));

        // check start command send to clients
        var strwpr = getLastHistoryCommandOfType(CommandType.START);
        assertNotNull(strwpr); // game must be started with 3 players
        var strcmd = strwpr.getCommand(StartCommand.class);
        assertEquals(3, strcmd.getPlayersID().length);
        assertEquals(3, strcmd.getUsername().length);
        // players should have the same order as login
        assertTrue(strcmd.getUsername()[0].equals("Gompachiro"));
        assertTrue(strcmd.getUsername()[1].equals("Nezuko"));
        assertTrue(strcmd.getUsername()[2].equals("Inosuke"));

        assertEquals(Game.GameState.GOD_FILTER, controller.getMatch().getCurrentState());

        // pick 3 gods
        controller.onCommand(FilterGodCommand.makeReply(1, SERVER_ID, new int[]{1,2,3}));

        assertEquals(Game.GameState.GOD_PICK, controller.getMatch().getCurrentState());

        //chose gods p2/3
        controller.onCommand(PickGodCommand.makeReply(2, SERVER_ID, 1));
        controller.onCommand(PickGodCommand.makeReply(3, SERVER_ID, 2));

        assertEquals(Game.GameState.FIRST_PLAYER_PICK, controller.getMatch().getCurrentState());

        //select first player
        controller.onCommand(FirstPlayerPickCommand.makeReply(1, SERVER_ID, 1));

        assertEquals(Game.GameState.WORKER_PLACE, controller.getMatch().getCurrentState());

        // place workers with following schema starting from 0,0:
        // p1 | p1 | p3
        // p2 | p2 | p3
        // this will make p1 lose at the beginning of the game
        controller.onCommand(WorkerPlaceCommand.makeWrapped(1, SERVER_ID, new Vector2[]{new Vector2(0,0), new Vector2(0,1)}));
        controller.onCommand(WorkerPlaceCommand.makeWrapped(2, SERVER_ID, new Vector2[]{new Vector2(1,0), new Vector2(1,1)}));
        controller.onCommand(WorkerPlaceCommand.makeWrapped(3, SERVER_ID, new Vector2[]{new Vector2(0,2), new Vector2(1,2)}));

        assertEquals(Game.GameState.GAME, controller.getMatch().getCurrentState());

        // notification that first client lost
        var lasteg = getLastHistoryCommandOfType(CommandType.END_GAME);
        assertNotNull(lasteg);
        var lcmd = lasteg.getCommand(EndGameCommand.class);
        assertEquals(1, lcmd.getTarget());
        assertTrue(lcmd.isTargetLoser());
        assertTrue(lcmd.isMatchStillRunning());
        assertFalse(lcmd.isMatchInterrupted());

        //check command order
        assertEquals(CommandType.END_GAME, commandHistory.get(commandHistory.size()-3).getType());
        assertEquals(CommandType.UPDATE, commandHistory.get(commandHistory.size()-2).getType());
        assertEquals(CommandType.ACTION_TIME, commandHistory.get(commandHistory.size()-1).getType());

        // check map
        var updwrp = getLastHistoryCommandOfType(CommandType.UPDATE);
        assertNotNull(updwrp);
        var updcmd = updwrp.getCommand(UpdateCommand.class);
        var map = updcmd.getUpdatedMap();
        // same cell heights and domes
        for(int x = 0; x < map.getHeight(); x++)
        {
            for(int y = 0; y < map.getLength(); y++)
            {
                int originalLvl = controller.getMatch().getCurrentMap().getLevel(new Vector2(x,y));
                boolean originalDome = controller.getMatch().getCurrentMap().isCellDome(new Vector2(x,y));
                assertEquals(originalLvl, map.getLevel(x,y));
                assertEquals(originalDome, map.isDome(x,y));
            }
        }
        //same workers
        assertEquals(controller.getMatch().getCurrentMap().getWorkers().size(), map.getWorkers().length);
        for(int i=0; i < map.getWorkers().length; i++)
        {
            var originalWorker = controller.getMatch().getCurrentMap().getWorkers().get(i);
            assertEquals(originalWorker.getId(), map.getWorkers()[i].getWorkerID());
            assertEquals(originalWorker.getOwner().getId(), map.getWorkers()[i].getOwnerID());
            assertEquals(originalWorker.getPosition(), map.getWorkers()[i].getPosition());
        }

    }

    @Test
    void shouldStartActiveUndoTimerAndSendLoseAfterTimeout() throws InterruptedException
    {
        // join with 2 players
        controller.onConnect(JoinCommand.makeRequest(1, SERVER_ID, "Gompachiro"));

        controller.onConnect(JoinCommand.makeRequest(2, SERVER_ID, "Nezuko"));

        controller.onCommand(StartCommand.makeRequest(1,SERVER_ID));

        // pick 2 gods
        // Prometheus (id 10) required to make this test simple
        controller.onCommand(FilterGodCommand.makeReply(1, SERVER_ID, new int[]{10,2}));

        //chose gods p2/3
        controller.onCommand(PickGodCommand.makeReply(2, SERVER_ID, 2));

        //select first player
        controller.onCommand(FirstPlayerPickCommand.makeReply(1, SERVER_ID, 1));

        // place workers with following schema starting from 0,0:
        // p1 | p1 | p2
        // D  |    | D
        //    | p2 |
        // this will make p1 lose at the beginning of the game
        controller.onCommand(WorkerPlaceCommand.makeWrapped(1, SERVER_ID, new Vector2[]{new Vector2(0,0), new Vector2(0,1)}));

        // we cheat and place domes
        controller.getMatch().getCurrentMap().buildDome(new Vector2(1,0));
        controller.getMatch().getCurrentMap().buildDome(new Vector2(1,2));

        controller.onCommand(WorkerPlaceCommand.makeWrapped(2, SERVER_ID, new Vector2[]{new Vector2(0,2), new Vector2(2,1)}));

        // p1 turn, we make a move to trigger the undo
        // with prometeus wi build first so we lock our capability to move up
        // and end up being blocked with only undo action left
        controller.onCommand(ActionCommand.makeReply(1, SERVER_ID, 1, 1, new Vector2(1,1)));
        var lastAct = controller.getLastSent().getCommand(ActionCommand.class);
        assertEquals(1, lastAct.getAvailableActions().length);
        assertTrue(lastAct.getAvailableActions()[0].isUndo());

        Thread.sleep(Turn.MAX_UNDO_MILLI + 1000); // wait for timeout

        assertTrue(controller.getMatch().isEnded()); // player lost due to missed undo as last move
        assertEquals(CommandType.END_GAME, controller.getLastSent().getType());
        var egc = controller.getLastSent().getCommand(EndGameCommand.class);
        assertEquals(2, egc.getWinnerID());
        assertFalse(egc.isMatchStillRunning());
        assertFalse(egc.isMatchInterrupted());

    }


    @Test
    void shouldWinGoingToLevelThree()
    {
        // join with 2 players
        controller.onConnect(JoinCommand.makeRequest(1, SERVER_ID, "Gompachiro"));
        controller.onConnect(JoinCommand.makeRequest(2, SERVER_ID, "Nezuko"));
        controller.onCommand(StartCommand.makeRequest(1,SERVER_ID));

        // pick 2 gods
        controller.onCommand(FilterGodCommand.makeReply(1, SERVER_ID, new int[]{10,2}));

        //chose gods p2/3
        controller.onCommand(PickGodCommand.makeReply(2, SERVER_ID, 2));

        //select first player
        controller.onCommand(FirstPlayerPickCommand.makeReply(1, SERVER_ID, 1));

        // place workers with following schema starting from 0,0:
        // p1 | p1 | p2 | p2
        // l3 |    |
        // this will make p1 lose at the beginning of the game
        controller.onCommand(WorkerPlaceCommand.makeWrapped(1, SERVER_ID, new Vector2[]{new Vector2(0,0), new Vector2(0,1)}));

        // we cheat and level 2 under p1 worker and place a level 3 near so we can win right at the beginning
        controller.getMatch().getCurrentMap().build(new Vector2(0,0));
        controller.getMatch().getCurrentMap().build(new Vector2(0,0));

        controller.getMatch().getCurrentMap().build(new Vector2(1,0));
        controller.getMatch().getCurrentMap().build(new Vector2(1,0));
        controller.getMatch().getCurrentMap().build(new Vector2(1,0));

        controller.onCommand(WorkerPlaceCommand.makeWrapped(2, SERVER_ID, new Vector2[]{new Vector2(0,2), new Vector2(0,3)}));

        controller.onCommand(ActionCommand.makeReply(1, SERVER_ID, 0,0, new Vector2(1,0)));

        var endGWrap = getLastHistoryCommandOfType(CommandType.END_GAME);
        assertNotNull(endGWrap);
        assertTrue(controller.getMatch().isEnded());
        assertEquals(1, endGWrap.getCommand(EndGameCommand.class).getWinnerID());

    }

    @Test
    void shouldEndGameIfAPlayerHasNoMoreMoves()
    {
        // join with 2 players
        controller.onConnect(JoinCommand.makeRequest(1, SERVER_ID, "Gompachiro"));
        controller.onConnect(JoinCommand.makeRequest(2, SERVER_ID, "Nezuko"));
        controller.onCommand(StartCommand.makeRequest(1,SERVER_ID));

        // pick 2 gods
        controller.onCommand(FilterGodCommand.makeReply(1, SERVER_ID, new int[]{10,2}));

        //chose gods p2/3
        controller.onCommand(PickGodCommand.makeReply(2, SERVER_ID, 2));

        //select first player
        controller.onCommand(FirstPlayerPickCommand.makeReply(1, SERVER_ID, 1));

        // place workers with following schema starting from 0,0:
        // p1 |    | p1 |
        // p2 | p2 |    |
        // this will make p1 lose at the beginning of the game
        controller.onCommand(WorkerPlaceCommand.makeWrapped(1, SERVER_ID, new Vector2[]{new Vector2(0,0), new Vector2(0,2)}));

        controller.onCommand(WorkerPlaceCommand.makeWrapped(2, SERVER_ID, new Vector2[]{new Vector2(1,0), new Vector2(1,1)}));

        // move back to
        // p1 | p1 |    |
        // p2 | p2 |    |
        controller.onCommand(ActionCommand.makeReply(1, SERVER_ID, 0,1, new Vector2(0,1)));

        // we cheat an place domes to trap p1 and force lose during this turn by running the build action
        controller.getMatch().getCurrentMap().buildDome(new Vector2(0,2));
        controller.getMatch().getCurrentMap().buildDome(new Vector2(1,2));

        controller.onCommand(ActionCommand.makeReply(1, SERVER_ID, 0, 1, new Vector2(0,2)));

        var endGWrap = getLastHistoryCommandOfType(CommandType.END_GAME);
        assertNotNull(endGWrap);
        assertTrue(controller.getMatch().isEnded());
        assertEquals(2, endGWrap.getCommand(EndGameCommand.class).getWinnerID());

    }

}