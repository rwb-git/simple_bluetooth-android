package xyz.fork20.mine.simple_bt;

import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import java.lang.ref.WeakReference;


class myHandler  extends Handler {

	private final WeakReference<MainActivity> myActivity;

	// this allows garbage collection to clean up even if delayed messages still link to finished activities.
	// I'm not sure that applies to MainActivity but it kept warning that this handler should be static
	// to avoid memory leaks so i found this approach on the web.


	public myHandler(MainActivity act){

		myActivity = new WeakReference<>(act);
	}

	@Override
	public void handleMessage(Message msg){

		MainActivity act2 = myActivity.get();

		if (act2 != null) {
    		   /*
                obtainMessage forms: what, arg1 and arg2 are integers. obj can be anything?
                                               args
                    empty = obtainMessage()     0    not sure what good this is
                    what                        1 int
                    what obj                    2 = 1 int and 1 object
                    what arg1 arg2              3 ints
                    what arg1 arg2 obj          4 = 3 ints and object
             */

			switch (msg.what) {

                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:

                            web_screen.add_a_text_line("btcs is connected");
                            break;

                        case BluetoothChatService.STATE_CONNECTING:

                            web_screen.add_a_text_line("btcs is connecting");
                            break;

                        case BluetoothChatService.STATE_LISTEN:
                            web_screen.add_a_text_line("listen");
                            break;

                        case BluetoothChatService.STATE_NONE:

                            web_screen.add_a_text_line("btcs is not connected");
                            break;
                    }
                    break;

                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    act2.mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    Toast.makeText(act2, "Connected to "
                            + act2.mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case Constants.MESSAGE_TOAST:
                    Toast.makeText(act2, msg.getData().getString(Constants.TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;

                case 163:
                    web_screen.add_a_text_line((String) msg.obj);
                    break;
                case 318:

					act2.sendbytes((byte[]) msg.obj);
					break;

				case 325:

					act2.insecure_scan();
					break;

                case 326:

                    act2.secure_scan();
                    break;

				case 328:

					act2.disconnect();
					break;

				case 329:
					Toast.makeText(act2,(String) msg.obj, Toast.LENGTH_LONG).show();
					break;
			}
		}
	}
}
