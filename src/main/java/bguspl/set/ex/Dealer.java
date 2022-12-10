package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
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

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        while (!shouldFinish()) {
            try {
                placeCardsOnTable();
            }
            catch (Exception e){
                System.out.println(e);
            }
            startTime = System.currentTimeMillis();
            timerLoop();
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

        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            try {
                placeCardsOnTable();
            }
            catch(Exception e){}
            for (Player p : players) {
                    if (p.tokenToSlots().size() == 3) {
                        if (isSet(p.tokenToSlots())) {
                            removeCardsBySlots(p.tokenToSlots());
                            p.point();
                            updateTimerDisplay(true);
                        }
                        else {
                            //p.penalty();
                        }
                    }
                }
            try {
                placeCardsOnTable();
            }
            catch (Exception e){
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
     * Checks if any cards should be removed from the table and returns them to the deck.
     */
    //TODO synchronized this method from players
    //TODO handle the case when a player chooses a legal set
    private void removeCardsFromTable() {
        List<Integer> currentSlots = Arrays.asList(table.slotToCard);
        if(env.util.findSets(currentSlots,1).size() == 0){
            removeAllCardsFromTable();
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() throws InterruptedException {
        List<Integer> slotNumbers = new ArrayList<>();
        for (int i = 0 ; i < 12 ;i++)
            slotNumbers.add(i);
        while (!slotNumbers.isEmpty()){

            Random rand = new Random();
            int index = rand.nextInt(slotNumbers.size());
            Integer j = slotNumbers.get(index);
            slotNumbers.remove(j);
            if(table.slotToCard[j]==null) {
                Thread.sleep(200);
                Random random = new Random();
                table.placeCard(deck.remove(random.nextInt(deck.size())),j);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
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
}
