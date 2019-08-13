package xyz.fork20.mine.simple_bt;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;


public class web_screen extends AppCompatActivity {

    public static Handler mHandler;

    private static ArrayAdapter<String> packet_log;

    public static void setup_the_log(Context c){ // this is called by MainActivity onCreate so that early events can be logged before this activity starts

        if (packet_log == null) {
            packet_log = new ArrayAdapter<>(c, R.layout.message);
        }

        packet_log.add("setup log");
    }


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.web_screen);

        ListView packet_view = findViewById(R.id.pkts);

        packet_view.setAdapter(packet_log);

        Button dump_sram = findViewById(R.id.dump_sram);
        dump_sram.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                send_one_byte();
            }
        });

        Button connect_button = findViewById(R.id.connect_button);
        connect_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                mHandler.obtainMessage(326).sendToTarget();
                finish();			// without this finish, the dialog looks ok but it does not connect.
            }
        });


        Button disconnect_button = findViewById(R.id.disconnect_button);
        disconnect_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                mHandler.obtainMessage(328).sendToTarget();
                finish();			// without this finish, the dialog looks ok but it does not connect.
            }
        });


        Button connect_insecure = findViewById(R.id.connect_insecure);
        connect_insecure.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                mHandler.obtainMessage(325).sendToTarget();
                finish();			// without this finish, the dialog looks ok but it does not connect.
            }
        });
    }

    public static void add_a_text_line(String s){

        if (packet_log != null) {
            packet_log.add(s);
        }

        assert packet_log != null;
        int c = packet_log.getCount();

        if (c > 500) { // I don't know that it matters, but wait for it to exceed 500 and then trim to 400 so we aren't trimming on every addition

            while (c > 400) {

                packet_log.remove(packet_log.getItem(0));

                packet_log.notifyDataSetChanged();

                c = packet_log.getCount();
            }
        }
    }


    private void send_one_byte(){

        BluetoothChatService.clean_up_mess(); // straighten out any crap that is left over from last exchange

        byte[] send2 = new byte[1];
        send2[0] = (byte) 66;
        mHandler.obtainMessage(318, send2).sendToTarget();  // send to avr

    }
}
