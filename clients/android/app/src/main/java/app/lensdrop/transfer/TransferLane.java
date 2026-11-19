package app.lensdrop.transfer;
import java.util.concurrent.atomic.AtomicBoolean;
/** Process-wide bounded transfer lane shared by photo and print workers. */
final class TransferLane {private static final AtomicBoolean BUSY=new AtomicBoolean(false);static boolean enter(){return BUSY.compareAndSet(false,true);}static void leave(){BUSY.set(false);}}
