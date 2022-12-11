package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    private long startTime;
    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private BlockingQueue<Player> playersToCheck;

    private Dictionary<Player,Long> playerToPenaltyTime;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        reshuffleTime = 60000;
        playersToCheck = new LinkedBlockingQueue<>();
        playerToPenaltyTime = new Hashtable<>();

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        for (Player p : this.players) {
            playerToPenaltyTime.put(p, (long) 0);
        }
        for (Player p :players){
            Thread t = new Thread(p,p.id+"");
            t.start();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            startTime = System.currentTimeMillis();
            timerLoop();
            Arrays.stream(players).forEach(Player::clearTokens);
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis()-startTime < reshuffleTime) {
            try{sleepUntilWokenOrTimeout();}
            catch (Exception e){throw new IllegalArgumentException(e.getMessage());}
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
            try {
               tokensValidation();
            }
            catch (Exception e){throw new IllegalArgumentException(e.getMessage());}
            for (Player p : players){
                if (playerToPenaltyTime.get(p) > System.currentTimeMillis())
                    env.ui.setFreeze(p.id,playerToPenaltyTime.get(p)-System.currentTimeMillis());
                else {
                    env.ui.setFreeze(p.id,0);
                    synchronized(p){
                        p.notify();
                    }
                }
            }
        }
    }

    private void tokensValidation() throws InterruptedException {
        if (playersToCheck.size()>0) {
            Player p = playersToCheck.take();
            if (isSet(p.tokenToSlots())) {
                removeCardsBySlots(p.tokenToSlots());
                p.point();
                updateTimerDisplay(true);
                playerToPenaltyTime.put(p,System.currentTimeMillis() + 1000);

//                Arrays.stream(players).forEach(Player::clearTokens);
            } else {
//                p.penalty();
                playerToPenaltyTime.put(p,System.currentTimeMillis() + 3000);

            }
            try {
                placeCardsOnTable();
            } catch (Exception e) {
            }
            synchronized (p) {
                p.wait();
            }
        }
    }





    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks if any cards should be removed from the table
     */
    //TODO synchronized this method from players
    //TODO handle the case when a player chooses a legal set
    private void removeCardsFromTable() {
        List<Integer> currentSlots = Arrays.asList(table.slotToCard);
        while (currentSlots.remove(null)) {
        }
        if(env.util.findSets(currentSlots,1).size() == 0){
            removeAllCardsFromTable();
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable(){
        List<Integer> slotNumbers = new ArrayList<>();
        for (int i = 0 ; i < 12 ;i++)
            slotNumbers.add(i);
        while (!slotNumbers.isEmpty()){
            Random rand = new Random();
            int index = rand.nextInt(slotNumbers.size());
            Integer j = slotNumbers.get(index);
            slotNumbers.remove(j);
            if(table.slotToCard[j]==null) {
                Random random = new Random();
                table.placeCard(deck.remove(random.nextInt(deck.size())),j);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private  void  sleepUntilWokenOrTimeout() throws InterruptedException {
        // TODO implement
        synchronized (this) {
            this.wait(250);
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        long elapsed =60000- (System.currentTimeMillis()-startTime);
        boolean warn = false;
        if (reset) {
            elapsed = 60000;
            startTime = System.currentTimeMillis();
        }
        else if(elapsed<=10000)
            warn = true;
        env.ui.setCountdown(elapsed,warn);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for(int i = 0; i < table.slotToCard.length; i++)
            if(table.slotToCard[i] != null) {
                deck.add(table.slotToCard[i]);
                table.removeCard(i);
            }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
    }
    private boolean isSet(List<Integer> setToTest) {
            int[] setToTestArray = new int[3];
            for (int i = 0; i < setToTest.size(); i++)
                setToTestArray[i] = table.slotToCard[setToTest.get(i)];
        return env.util.testSet(setToTestArray);
    }
    private void removeCardsBySlots(List<Integer> slots){
        for (int i = 0; i < slots.size(); i ++)
            table.removeCard(slots.get(i));
    }
    public void addToPlayersQueue(Player p){
        playersToCheck.add(p);
    }


}
