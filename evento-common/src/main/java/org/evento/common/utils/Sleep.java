package org.evento.common.utils;

/**
 * The Sleep class provides a convenient method to pause the execution of the current thread for a specified amount of time.
 */
public class Sleep {

    /**
     * Pauses the execution of the current thread for a specified amount of time.
     *
     * @param milliseconds the number of milliseconds to sleep
     */
    public static void apply(long milliseconds){
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
