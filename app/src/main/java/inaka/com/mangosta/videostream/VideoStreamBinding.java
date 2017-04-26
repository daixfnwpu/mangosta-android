package inaka.com.mangosta.videostream;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.util.Pair;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.ice4j.TransportAddress;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jxmpp.stringprep.XmppStringprepException;

import inaka.com.mangosta.xmpp.XMPPSession;

/**
 * Created by rafalslota on 26/04/2017.
 */
public class VideoStreamBinding implements BindingConfirmator, StanzaListener {
    private static final String TAG = "VideoStreamBinding";
    private final ProxyRTPServer proxyRTP;
    private final UserInterface userInterface;

    public VideoStreamBinding(ProxyRTPServer proxyRTP, Activity activity) {
        this.proxyRTP = proxyRTP;
        this.userInterface = new UserInterface(activity);
    }

    public boolean startBinding() {
        userInterface.showStreamFromAlert(this);
        return false;
    }

    private void sendBinding(String jid, Pair<TransportAddress, TransportAddress> relays) {
        userInterface.debugToast("JID: " + jid + " && Data: " + relays.first.getHostAddress() + ":" +
                relays.first.getPort() + " && Control: " + relays.second.getHostAddress() +
                ":" + relays.second.getPort());


        if(!XMPPSession.isInstanceNull() && XMPPSession.getInstance().isConnectedAndAuthenticated()) {
            XMPPSession xmpp = XMPPSession.getInstance();
            try {
                String messageText = relays.first.getHostAddress() + ":" +
                        relays.first.getPort() + ";" + relays.second.getHostAddress() +
                        ":" + relays.second.getPort();
                xmpp.sendStanza(new Message(jid, messageText));
                xmpp.getXMPPConnection().addSyncStanzaListener(this, new StanzaFilter() {
                    @Override
                    public boolean accept(Stanza stanza) {
                        return stanza.getClass() == Message.class;
                    }
                });
            } catch (SmackException.NotConnectedException | InterruptedException | XmppStringprepException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "No XMPP session!");
        }
    }

    public UserInterface getUserInterface() {
        return userInterface;
    }

    @Override
    public void confirmBinding(String jid) {
        sendBinding(jid, proxyRTP.getRelayAddrs());
    }

    @Override
    public void processPacket(Stanza stanza) throws SmackException.NotConnectedException, InterruptedException {
        Log.d(TAG, "stanza received: " + stanza.toString());
    }

    public class UserInterface {

        private final Activity activity;

        public UserInterface(Activity activity) {
            this.activity = activity;
        }

        public Context getActivity() {
            return activity;
        }

        public void debugToast(String s) {
            Toast.makeText(getActivity(), s, Toast.LENGTH_LONG).show();
        }

        public void showNotReadyError() {
            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("VideoStream not ready");
            builder.setMessage("TURN data relay is not ready yet. Please try again later.");

            builder.setNeutralButton("Continue", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            AlertDialog alert = builder.create();
            alert.show();
        }

        public void showStreamFromAlert(final BindingConfirmator confirmator) {
            AlertDialog.Builder builder = new AlertDialog.Builder(userInterface.getActivity());
            builder.setTitle("Enter JID to stream from:");

            final EditText input = new EditText(userInterface.getActivity());
            input.setText("mypi@erlang-solutions.com");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT);
            input.setLayoutParams(lp);
            builder.setView(input);


            builder.setPositiveButton("Stream!", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                    confirmator.confirmBinding(input.getText().toString());
                }
            });

            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            AlertDialog alert = builder.create();
            alert.show();
        }
    }
}