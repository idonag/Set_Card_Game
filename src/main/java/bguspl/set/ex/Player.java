package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {
    private BlockingQueue<Integer> playerPresses;
    private List<Integer> playerTokens;
    private boolean changeAfterPenalty;


    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    Dealer dealer;
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        playerTokens = new LinkedList<>();
        playerPresses = new LinkedBlockingQueue<>();
        changeAfterPenalty = true;
        this.dealer = dealer;

    }

    public List<Integer> tokenToSlots(){
        return playerTokens;
    }
    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            try {
                step();
            }
            catch (Exception e){}
            if(playerTokens.size() == 3 && changeAfterPenalty ) {
                dealer.addToPlayersQueue(this);
                synchronized (dealer) {
                    dealer.notifyAll();
                }
                try {
                    synchronized (this) {
                        this.wait();
                    }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

            }

        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                // TODO implement player key press simulator
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        playerPresses.add(slot);
    }
    public void step() throws InterruptedException {
        int slot = playerPresses.take();
        if (playerTokens.contains(slot)){
            playerTokens.remove((Integer) slot);
            table.removeToken(this.id,slot);
            changeAfterPenalty=true;
        }
        else {
            if (playerTokens.size() < 3) {
                table.placeToken(this.id, slot);
                playerTokens.add(slot);
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    //TODO: how to prevent player from doing any action while being freezed
    public void penalty() {
        changeAfterPenalty=false;

        }


        //TODO show timer on screen near player's name


    public int getScore() {
        return score;
    }

    public void clearTokens(List<Integer> slots) {
        for (Integer token:playerTokens) {
            for (Integer j:slots) {
                if(token.equals(j)) {
                    table.removeToken(this.id, token);
                    System.out.println("1");
                    playerTokens.remove(token);
                    System.out.println("2");
                }
            }
        }

    }
}
