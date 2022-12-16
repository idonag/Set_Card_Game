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
        placeCardsOnTable();
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
            //Arrays.stream(players).forEach(Player::clearTokens);
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
          //  System.out.println("dealer woke up");
            updateTimerDisplay(false);
          //  System.out.println("dealer remove cards");
            removeCardsFromTable();
          //  System.out.println("dealer place cards");
            placeCardsOnTable();
            try {

               tokensValidation();
               // System.out.println("dealer validatis cards");
            }
            catch (InterruptedException e) {
                System.out.println(e.getMessage());
            }
            for (Player p : players){
                //System.out.println("players loop");

                if (playerToPenaltyTime.get(p) > System.currentTimeMillis())
                    env.ui.setFreeze(p.id,playerToPenaltyTime.get(p)-System.currentTimeMillis());
                else {
                    //System.out.println("before sync");
                    env.ui.setFreeze(p.id,0);
                    synchronized(p){
                     //   System.out.println("before notify player");
                        p.notify();
                    }
                }
            }
            terminate = zeroSetsLeft();
        }
    }

    private boolean zeroSetsLeft() {
        List<Integer> currentSlots = Arrays.asList(table.slotToCard);
        currentSlots = currentSlots.parallelStream().filter(Objects::nonNull).collect(Collectors.toList()); //remove null values
        return env.util.findSets(deck, 1).size() == 0 && env.util.findSets(currentSlots,1).size() == 0 ;
    }

    private void tokensValidation() throws InterruptedException {
       // System.out.println("enters token val");
        if (playersToCheck.size()>0) {
            Player p = playersToCheck.take();
            if (isSet(p.tokenToSlots())) {
                Vector<Integer> slotsToRemove = new Vector<>();
                slotsToRemove.addAll(p.tokenToSlots());
                removeCardsBySlots(slotsToRemove);
                p.point();
                System.out.println(p.tokenToSlots());
                updateTimerDisplay(true);
                playerToPenaltyTime.put(p,System.currentTimeMillis() + 1000);

            } else {
                p.penalty();
                playerToPenaltyTime.put(p,System.currentTimeMillis() + 3000);

            }
            try {
                placeCardsOnTable();
            } catch (Exception e) {
                throw e;
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
        return terminate || env.util.findSets(deck, 1).size() == 0 ;
    }

    /**
     * Checks if any cards should be removed from the table
     */
    //TODO synchronized this method from players
    //TODO handle the case when a player chooses a legal set
    private void removeCardsFromTable() {
        List<Integer> currentSlots = Arrays.asList(table.slotToCard);
         currentSlots = currentSlots.parallelStream().filter(Objects::nonNull).collect(Collectors.toList()); //remove null values
        if(env.util.findSets(currentSlots,1).size() == 0){
            removeAllCardsFromTable();
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable(){
            List<Integer> slotNumbers = new ArrayList<>();
            for (int i = 0; i < 12; i++)
                slotNumbers.add(i);
            while (!slotNumbers.isEmpty()) {
                Random rand = new Random();
                int index = rand.nextInt(slotNumbers.size());
                Integer j = slotNumbers.get(index);
                slotNumbers.remove(j);
                if (table.slotToCard[j] == null) {
                    Random random = new Random();
                    //deck.remove(random.nextInt(deck.size())),j
                    if (!deck.isEmpty()) {
                        System.out.println("cards in deck:"+deck.size());
                        table.placeCard(deck.remove(0), j);
                    }
                }

        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private  void  sleepUntilWokenOrTimeout() throws InterruptedException {
        // TODO implement
        synchronized (this) {
            this.wait(50);
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
        int maxPoint = 0;

        for (Player p : players) {
            if (p.getScore() > maxPoint)
                maxPoint = p.getScore();
        }
        List<Integer> winners = new LinkedList();
        for (Player p : players){
            if (p.getScore() == maxPoint)
                winners.add(p.id);
        }
        int[] winnersArray = new int[winners.size()];
        for (int i = 0; i < winnersArray.length;i++){
            winnersArray[i] = winners.get(i);
        }
        env.ui.announceWinner(winnersArray);

    }
    private boolean isSet(List<Integer> setToTest) {
            int[] setToTestArray = new int[3];
            for (int i = 0; i < setToTest.size(); i++)
                setToTestArray[i] = table.slotToCard[setToTest.get(i)];
        return env.util.testSet(setToTestArray);
    }
    private void removeCardsBySlots(Vector<Integer> slots){
        System.out.println("remove card from slots: "+slots);
        for (Player p: players) {
            for (Integer j:p.tokenToSlots()) {
                if(slots.contains(j)) {
                    table.removeToken(p.id,j);
                }
            }
        }
        for (int i = 0; i < slots.size(); i ++) {
            table.removeCard(slots.get(i));
        }
        System.out.println(players[0].tokenToSlots().toString());
        System.out.println(players[1].tokenToSlots().toString());

        for (Player p:players) {
            for (int s : slots)
                p.clearTokens(s);
        }
    }
    public void addToPlayersQueue(Player p){
        playersToCheck.add(p);
    }


}
