package bguspl.set.ex;

import bguspl.set.Env;
import java.util.Vector;
import java.util.Random;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * Game entities.
     */
    private final Dealer dealer;
    
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
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;
    
    /**
     * The players action queue.
     */
    private Vector<Integer> actions;
    
    /**
     * The players current tokens.
     */
    private Vector<Integer> tokens;
    
    /**
     * The the player is freezed.
     */
    private boolean freezed;
    
    /**
     * The sleep time to emulate a real player.
     */
    private int botTiming = 100;

    
    /**
     * Signifies the first element 
     */
    private final int first = 0;
    
    /**
     * Signifies the first element 
     */
    private final int unFreeze = 0;
    
    /**
     * Signifies the first element 
     */
    private boolean won;
    
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
        this.dealer = dealer;
        
        score = 0;
        actions = new Vector<Integer>();
        tokens = new Vector<Integer>();
        freezed = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
        	synchronized(this) {
	        	while (dealer.shuffleStatus()) {
	        		try {
	        			this.wait();
	        		}
	        		catch(InterruptedException error) {
	        		}
	        		
        			if (!human)
		        		synchronized(aiThread) {	
		        				aiThread.notify();
		        		}
	        	}
        	}
        	
        	
        	keyAction();
        }
        if (!human) {
        	synchronized(aiThread) {
        		aiThread.notify();
        	}
        	try { aiThread.join(); } 
            catch (InterruptedException error) {}
        }

        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
            	Random rand = new Random();
            	synchronized(aiThread) {
	            	while(dealer.shuffleStatus()) {
	                	try {
	                		aiThread.wait(botTiming);
	                    } catch (InterruptedException error) {}
	            	}
            	}
            	
            	keyPressed(rand.nextInt(env.config.tableSize));
            	
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    	terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
    	if (actions.size() <= Dealer.setSize && !this.dealer.shuffleStatus() && table.slotToCard[slot] != null
    			&& !freezed) 
    		actions.add(slot);
    	
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement

    	score++;
    	env.ui.setScore(id, score);
    	freezed = true;
    	try {
    		env.ui.setFreeze(id, env.config.pointFreezeMillis);
    		if (!human)
    			aiThread.sleep(env.config.pointFreezeMillis);
    		playerThread.sleep(env.config.pointFreezeMillis);
    	}
    	catch(InterruptedException error) {}
    	env.ui.setFreeze(id, unFreeze);
    	freezed = false;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
    	try {
    		env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
    		freezed = true;
    		if (!human)
    			aiThread.sleep(env.config.penaltyFreezeMillis);
    		playerThread.sleep(env.config.penaltyFreezeMillis);
    	}
    	catch(InterruptedException error) {}
    	freezed = false;
    	env.ui.setFreeze(id, unFreeze);
    }

    public int score() {
        return score;
    }
    
    public void setWon(boolean result) {
        won = result;
    }
    
    
    public void keyAction() {
		if (actions.isEmpty()) 
			return;
		Integer slot = actions.remove(first);
		
		// slot already holds a token, therefore removes it
		if (tokens.contains(slot)) {
			table.removeToken(id, slot);
			tokens.remove(slot);
			return;
		}
		
		int amountOfCards = table.getAmountOfPlayersCards(id);
		// if there are all ready 3 cards belonging to the player(he has 3 tokens down) dont place another 
		// or even check for legality as its already been checked 
		if (amountOfCards == Dealer.setSize)
			return;
		
		// else, put the token in the right slot
		tokens.add(slot);
		table.placeToken(id, slot);
		amountOfCards++;
		// 3 tokens are placed, need to check for set
		if (amountOfCards == Dealer.setSize) {
			synchronized(dealer) {
				dealer.addPlayerToCheck(id);
				dealer.notify();
			}
			synchronized(this) {
				try {
					wait();
				}
				catch(InterruptedException error) {}
			}
			if (won)
				point();
			else
				penalty();
		}
		
	}
    
    
}
