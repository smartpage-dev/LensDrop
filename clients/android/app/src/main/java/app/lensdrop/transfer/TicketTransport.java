package app.lensdrop.transfer;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One foreground, manually initiated operation; secure SPP only. No BLE, discovery,
 * reconnect, retry, raw settings or print-completion inference. Permissions checked by UI. */
@SuppressLint("MissingPermission")
final class TicketTransport implements AutoCloseable {
    static final AtomicBoolean BUSY=new AtomicBoolean(false);
    static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private final AtomicBoolean cancelled=new AtomicBoolean(false);
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private BluetoothSocket socket;
    private volatile boolean writeAttempted;
    boolean writeAttempted(){return writeAttempted;}
    private void check()throws InterruptedIOException{if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("ticket_cancelled");}
    private synchronized void attach(BluetoothSocket next)throws IOException{
        if(cancelled.get()){next.close();throw new InterruptedIOException("ticket_cancelled");}socket=next;
    }
    void connect(BluetoothAdapter adapter,String address)throws Exception{
        TicketProtocol.printer(address);check();
        if(adapter==null||!adapter.isEnabled())throw new IOException("bluetooth_unavailable");
        adapter.cancelDiscovery();
        BluetoothDevice device=adapter.getRemoteDevice(address);
        if(device.getBondState()!=BluetoothDevice.BOND_BONDED)throw new IOException("printer_not_paired");
        BluetoothSocket s=device.createRfcommSocketToServiceRecord(SPP);attach(s);
        ScheduledFuture<?> deadline=timer.schedule(this::cancel,12,TimeUnit.SECONDS);
        try{check();s.connect();check();}finally{deadline.cancel(false);}
    }
    void send(byte[] packet)throws Exception{
        if(packet==null||packet.length==0||packet.length>2048)throw new IllegalArgumentException("ticket_size");
        BluetoothSocket s;synchronized(this){s=socket;}
        if(s==null||!s.isConnected())throw new IOException("printer_not_connected");
        ScheduledFuture<?> deadline=timer.schedule(this::cancel,30,TimeUnit.SECONDS);
        try{
            check();OutputStream out=s.getOutputStream();
            for(int at=0;at<packet.length;){
                check();int n=Math.min(TicketProtocol.CHUNK_BYTES,packet.length-at);
                writeAttempted=true; // BEFORE write: an exception can occur after partial transmission.
                out.write(packet,at,n);at+=n;
                if(at<packet.length)Thread.sleep(TicketProtocol.CHUNK_DELAY_MS);
            }
            out.flush();check();
            // Small bounded drain interval, not proof that printer accepted or printed the packet.
            Thread.sleep(400);check();
        }finally{deadline.cancel(false);}
    }
    void cancel(){cancelled.set(true);BluetoothSocket s;synchronized(this){s=socket;socket=null;}if(s!=null)try{s.close();}catch(IOException ignored){}}
    @Override public void close(){cancel();timer.shutdownNow();}
}
