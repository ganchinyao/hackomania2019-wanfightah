package com.says.hackomonia.myapplication;

import android.Manifest;
import android.animation.Animator;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.GamesCallbackStatusCodes;
import com.google.android.gms.games.GamesClient;
import com.google.android.gms.games.GamesClientStatusCodes;
import com.google.android.gms.games.InvitationsClient;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.PlayersClient;
import com.google.android.gms.games.RealTimeMultiplayerClient;
import com.google.android.gms.games.multiplayer.Invitation;
import com.google.android.gms.games.multiplayer.InvitationCallback;
import com.google.android.gms.games.multiplayer.Multiplayer;
import com.google.android.gms.games.multiplayer.Participant;
import com.google.android.gms.games.multiplayer.realtime.OnRealTimeMessageReceivedListener;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessage;
import com.google.android.gms.games.multiplayer.realtime.Room;
import com.google.android.gms.games.multiplayer.realtime.RoomConfig;
import com.google.android.gms.games.multiplayer.realtime.RoomStatusUpdateCallback;
import com.google.android.gms.games.multiplayer.realtime.RoomUpdateCallback;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class MultiPlayerGame extends AppCompatActivity {
    final static String TAG = "gggg";
    private HandshakeUI handshakeUI;
    private HandshakeConnection handshakeConnection;
    // All click listener event to be put into this class
    private ClickListener clickListener = new ClickListener();
    private boolean inGame = false; // set to true when actual game starts
    private MultiPlayerActualGame multiPlayerActualGame;
    HashMap<String, PlayerIcon> playerIconImageViewMap = new HashMap<>();
    private int amountOfPlayersWantToRestart = 0; // used in restart button
    boolean isAppRunning = false;
    private boolean onResumeCalledBefore = false;
    private boolean goingToPreviousActivity = false;
    boolean exitFromGameToMainMenu = false; // this flag is used to control media sound
    private ImageView playButton;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    class PlayerIcon {
        private ImageView playerIcon;
        private boolean isReady;

        public PlayerIcon(ImageView playerIcon) {
            this.playerIcon = playerIcon;

            isReady = false;
        }

        public boolean isReady() {
            return isReady;
        }

        public ImageView getPlayerIcon() {
            return playerIcon;
        }

        // set in ready in category selection means player is ready,
        // in restart menu means player wants to restart
        public void setIsReady(boolean value) {
            this.isReady = value;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.multiplayer_layout);
        playButton = findViewById(R.id.multiplayer_signIn_Button
        );
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initializeHandshake();
            }
        });

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onBackPressed() {
        multiPlayerActualGame.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (!goingToPreviousActivity) {
            MusicManager.stopMediaPlayer();
        }

        isAppRunning = false;

        if (exitFromGameToMainMenu) {
            MusicManager.isGoingNextActivity = false;
        } else {
            MusicManager.isGoingNextActivity = true;
        }
    }


    private void startQuickGame() {
        // quick-start a game with 1 randomly selected opponent
        final int MIN_OPPONENTS = 1, MAX_OPPONENTS = 7;
        Bundle autoMatchCriteria = RoomConfig.createAutoMatchCriteria(MIN_OPPONENTS,
                MAX_OPPONENTS, 0);
        handshakeConnection.keepScreenOn();
        handshakeConnection.mRoomConfig = RoomConfig.builder(handshakeConnection.mRoomUpdateCallback)
                .setOnMessageReceivedListener(handshakeConnection.mOnRealTimeMessageReceivedListener)
                .setRoomStatusUpdateCallback(handshakeConnection.mRoomStatusUpdateCallback)
                .setAutoMatchCriteria(autoMatchCriteria)
                .build();
        handshakeConnection.mRealTimeMultiplayerClient.create(handshakeConnection.mRoomConfig);
    }


    private void initializeHandshake() {
        handshakeUI = new HandshakeUI(clickListener, (ImageView) findViewById(R.id.multiplayer_signIn_Button),
                (ImageView) findViewById(R.id.multiplayer_backbutton), (ImageView) findViewById(R.id.multiplayer_inviteFriends_Button),
                (ImageView) findViewById(R.id.multiplayer_seeInvitation_Button), (ImageView) findViewById(R.id.multiplayer_signOut_Button),
                (ImageView) findViewById(R.id.googleIconImageView), (ImageView) findViewById(R.id.multiplayer_inviteFriendHighFiveIcon),
                (ImageView) findViewById(R.id.multiplayer_seeInvitationMailIcon), (RelativeLayout) findViewById(R.id.multiplayer_invitationRelativeLayout),
                (Button) findViewById(R.id.multiplayer_invitationAcceptButton), (TextView) findViewById(R.id.multiplayer_invitation_questionTextView),
                (TextView) findViewById(R.id.signinHelpText), (TextView) findViewById(R.id.signInTextView),
                (TextView) findViewById(R.id.multiplayer_signOutTextView), (TextView) findViewById(R.id.multiplayer_inviteFriendsTextView),
                (TextView) findViewById(R.id.multiplayer_seeInvitationTextView), (ImageView) findViewById(R.id.multiplayer_quickPlay_Button),
                (TextView) findViewById(R.id.multiplayer_quickPlayTextView), (ImageView) findViewById(R.id.multiplayer_quickPlayIcon),
                (TextView) findViewById(R.id.multiplayer_waitscreen_loadingTextView), (ProgressBar) findViewById(R.id.multiplayer_waitscreenprogressbar));
        handshakeConnection = new HandshakeConnection(this, handshakeUI);
    }

    void restartYesOrNo(String participantId, boolean wantToRestart) {
        PlayerIcon playerIcon = playerIconImageViewMap.get(participantId);

        if (wantToRestart) {
            amountOfPlayersWantToRestart++;
            playerIcon.setIsReady(true);
            setPlayerIconToFullColor(playerIcon.getPlayerIcon());

            if (amountOfPlayersWantToRestart == handshakeConnection.mParticipants.size()) {
                // prevent further clicking so that we can restart properly
                if (multiPlayerActualGame.restartButton != null)
                    multiPlayerActualGame.restartButton.setClickable(false);

                // deselect all ready so that on category selection it work as correct
                for (PlayerIcon currentPlayIcon : playerIconImageViewMap.values()) {
                    currentPlayIcon.setIsReady(false);
                }

                multiPlayerActualGame.onRestartChanges();
            }
        } else {
            amountOfPlayersWantToRestart--;
            playerIcon.setIsReady(false);
            setPlayerIconToGray(playerIcon.getPlayerIcon());
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        isAppRunning = true;
        onResumeCalledBefore = true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == Constants.RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(intent);

            if (result.isSuccess()) {
                // The signed in account is stored in the result.
                GoogleSignInAccount signedInAccount = result.getSignInAccount();
                handshakeConnection.onConnected(signedInAccount);
                Utils.setIfJustSignedIn(MultiPlayerGame.this, true);

            } else {
                String message = result.getStatus().getStatusMessage();
                if (message == null || message.isEmpty()) {
                    message = getString(R.string.signin_other_error);
                }
                new AlertDialog.Builder(this).setMessage(message)
                        .setNeutralButton(android.R.string.ok, null).show();
            }
        } else if (requestCode == Constants.RC_SELECT_PLAYERS) {
            // we got the result from the "select players" UI -- ready to create the room
            handshakeConnection.handleSelectPlayersResult(resultCode, intent);

        } else if (requestCode == Constants.RC_INVITATION_INBOX) {
            // we got the result from the "select invitation" UI (invitation inbox). We're
            // ready to accept the selected invitation:
            handshakeConnection.handleInvitationInboxResult(resultCode, intent);

        } else if (requestCode == Constants.RC_WAITING_ROOM) {
            // we got the result from the "waiting room" UI.
            if (resultCode == Activity.RESULT_OK) {
                // ready to start playing
                Log.d(TAG, "Starting game (waiting room returned OK).");
                startGame();
            } else if (resultCode == GamesActivityResultCodes.RESULT_LEFT_ROOM) {
                // player indicated that they want to leave the room
                handshakeConnection.leaveRoom();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // Dialog was cancelled (user pressed back key, for instance). In our game,
                // this means leaving the room too. In more elaborate games, this could mean
                // something else (like minimizing the waiting room UI).
                handshakeConnection.leaveRoom();
            }
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    private void setPlayerIconToGray(ImageView playerIcon) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0);  //0 means grayscale
        ColorMatrixColorFilter cf = new ColorMatrixColorFilter(matrix);
        playerIcon.setColorFilter(cf);
        playerIcon.setImageAlpha(128);   // 128 = 0.5
    }

    private void setPlayerIconToFullColor(ImageView playerIcon) {
        playerIcon.setColorFilter(null);
        playerIcon.setImageAlpha(255);
    }

    // Start the gameplay phase of the game.
    private void startGame() {
        inGame = true;
        handshakeConnection.switchToScreen(Screen.ACTUAL_GAME);

        multiPlayerActualGame = new MultiPlayerActualGame(this, handshakeConnection);
    }

    // opponent score increases, hence update their score in ranking
    private void updateOpponentScore(byte id, byte scoreToUpdate) {
        multiPlayerActualGame.updateOpponentScore(id, scoreToUpdate);
    }

    class HandshakeUI {
        private ImageView signInButton, backButton, inviteFriends_Button, seeInvitation_Button,
                signOutButton, googleIconImageView, inviteFriendsImageView, seeInvitationImageView,
                quickPlayButton, quickPlayImageView;
        private RelativeLayout invitation_relativeLayout;
        private Button invitation_acceptButton;
        private TextView invitation_questionTextView;
        private TextView signIn_helpTextView, signIn_TextView, signOutTextView, inviteFriendsTextView,
                seeInvitationTextView, quickPlayTextView, waitscreen_loadingTextView;
        private ProgressBar waitscreen_progressBar;

        HandshakeUI(ClickListener clickListener, ImageView signInButton, ImageView backButton,
                    ImageView inviteFriends_Button, ImageView seeInvitation_Button,
                    ImageView signOutButton, ImageView googleIconImageView,
                    ImageView inviteFriendsImageView, ImageView seeInvitationImageView,
                    RelativeLayout invitation_relativeLayout, Button invitation_acceptButton,
                    TextView invitation_questionTextView, TextView signIn_helpTextView,
                    TextView signIn_TextView, TextView signOutTextView, TextView inviteFriendsTextView,
                    TextView seeInvitationTextView, ImageView quickPlay_Button, TextView quickPlayTextView,
                    ImageView quickPlayImageView, TextView waitscreen_loadingTextView, ProgressBar progressBar) {
            this.signInButton = signInButton;
            this.backButton = backButton;
            this.inviteFriends_Button = inviteFriends_Button;
            this.seeInvitation_Button = seeInvitation_Button;
            this.signOutButton = signOutButton;
            this.googleIconImageView = googleIconImageView;
            this.inviteFriendsImageView = inviteFriendsImageView;
            this.seeInvitationImageView = seeInvitationImageView;
            this.invitation_relativeLayout = invitation_relativeLayout;
            this.invitation_acceptButton = invitation_acceptButton;
            this.invitation_questionTextView = invitation_questionTextView;
            this.signIn_helpTextView = signIn_helpTextView;
            this.signIn_TextView = signIn_TextView;
            this.signOutTextView = signOutTextView;
            this.inviteFriendsTextView = inviteFriendsTextView;
            this.seeInvitationTextView = seeInvitationTextView;
            this.quickPlayButton = quickPlay_Button;
            this.quickPlayTextView = quickPlayTextView;
            this.quickPlayImageView = quickPlayImageView;
            this.waitscreen_loadingTextView = waitscreen_loadingTextView;
            this.waitscreen_progressBar = progressBar;

            this.signInButton.setOnClickListener(clickListener);
            this.backButton.setOnClickListener(clickListener);
            this.quickPlayButton.setOnClickListener(clickListener);
            this.inviteFriends_Button.setOnClickListener(clickListener);
            this.seeInvitation_Button.setOnClickListener(clickListener);
            this.signOutButton.setOnClickListener(clickListener);
            this.invitation_acceptButton.setOnClickListener(clickListener);
        }

        public TextView getWaitscreen_loadingTextView() {
            return waitscreen_loadingTextView;
        }

        public ProgressBar getWaitscreen_progressBar() {
            return waitscreen_progressBar;
        }

        public ImageView getSignInButton() {
            return signInButton;
        }

        public ImageView getBackButton() {
            return backButton;
        }

        public ImageView getInviteFriends_Button() {
            return inviteFriends_Button;
        }

        public ImageView getSeeInvitation_Button() {
            return seeInvitation_Button;
        }

        public ImageView getSignOutButton() {
            return signOutButton;
        }

        public ImageView getGoogleIconImageView() {
            return googleIconImageView;
        }

        public ImageView getInviteFriendsImageView() {
            return inviteFriendsImageView;
        }

        public ImageView getSeeInvitationImageView() {
            return seeInvitationImageView;
        }

        public RelativeLayout getInvitation_relativeLayout() {
            return invitation_relativeLayout;
        }

        public Button getInvitation_acceptButton() {
            return invitation_acceptButton;
        }

        public ImageView getQuickPlayButton() {
            return quickPlayButton;
        }

        public ImageView getQuickPlayImageView() {
            return quickPlayImageView;
        }

        public TextView getQuickPlayTextView() {
            return quickPlayTextView;
        }

        public TextView getInvitation_questionTextView() {
            return invitation_questionTextView;
        }

        public TextView getSignIn_helpTextView() {
            return signIn_helpTextView;
        }

        public TextView getSignIn_TextView() {
            return signIn_TextView;
        }

        public TextView getSignOutTextView() {
            return signOutTextView;
        }

        public TextView getInviteFriendsTextView() {
            return inviteFriendsTextView;
        }

        public TextView getSeeInvitationTextView() {
            return seeInvitationTextView;
        }
    }

    class HandshakeConnection {
        final static String TAG = "gggg";
        private HandshakeUI handshakeUI;
        private Context context;

        // Client used to sign in with Google APIs
        private GoogleSignInClient mGoogleSignInClient = null;

        // Client used to interact with the real time multiplayer system.
        RealTimeMultiplayerClient mRealTimeMultiplayerClient = null;

        // Client used to interact with the Invitation system.
        private InvitationsClient mInvitationsClient = null;

        // Room ID where the currently active game is taking place; null if we're
        // not playing.
        String mRoomId = null;

        // Holds the configuration of the current room.
        RoomConfig mRoomConfig;

        // The participants in the currently active game
        ArrayList<Participant> mParticipants = null;

        // My participant ID in the currently active game
        String mMyId = null;

        // If non-null, this is the id of the invitation we received via the
        // invitation listener
        String mIncomingInvitationId = null;

        HandshakeConnection(Context context, HandshakeUI handshakeUI) {
            // Create the client used to sign in.
            mGoogleSignInClient = GoogleSignIn.getClient(MultiPlayerGame.this,
                    new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
                            // Since we are using SavedGames, we need to add the SCOPE_APPFOLDER to access Google Drive.
                            .build());
            this.handshakeUI = handshakeUI;
            this.context = context;
        }

        HashMap<String, String> playerNameMap = new HashMap<>(); // map player google display name to their in game display name

        /*
         * CALLBACKS SECTION. This section shows how we implement the several games
         * API callbacks.
         */
        private String mPlayerId;

        // The currently signed in account, used to check the account has changed outside of this activity when resuming.
        GoogleSignInAccount mSignedInAccount = null;

        void onConnected(GoogleSignInAccount googleSignInAccount) {
            Log.d(TAG, "onConnected(): connected to Google APIs");

            Constants.signedIn = true;
            if (mSignedInAccount != googleSignInAccount) {

                mSignedInAccount = googleSignInAccount;

                // update the clients
                mRealTimeMultiplayerClient = Games.getRealTimeMultiplayerClient(context, googleSignInAccount);
                mInvitationsClient = Games.getInvitationsClient(context, googleSignInAccount);

                // get the playerId from the PlayersClient
                PlayersClient playersClient = Games.getPlayersClient(context, googleSignInAccount);
                playersClient.getCurrentPlayer()
                        .addOnSuccessListener(new OnSuccessListener<Player>() {
                            @Override
                            public void onSuccess(Player player) {
                                mPlayerId = player.getPlayerId();

                                switchToMainScreen();
                            }
                        })
                        .addOnFailureListener(createFailureListener("There was a problem getting the player id!"));
            }

            // register listener so we are notified if we receive an invitation to play
            // while we are in the game
            mInvitationsClient.registerInvitationCallback(mInvitationCallback);

            // get the invitation from the connection hint
            // Retrieve the TurnBasedMatch from the connectionHint
            GamesClient gamesClient = Games.getGamesClient(context, googleSignInAccount);
            gamesClient.getActivationHint()
                    .addOnSuccessListener(new OnSuccessListener<Bundle>() {
                        @Override
                        public void onSuccess(Bundle hint) {
                            if (hint != null) {
                                Invitation invitation =
                                        hint.getParcelable(Multiplayer.EXTRA_INVITATION);

                                if (invitation != null && invitation.getInvitationId() != null) {
                                    // retrieve and cache the invitation ID
                                    Log.d(TAG, "onConnected: connection hint has a room invite!");
                                    acceptInviteToRoom(invitation.getInvitationId());
                                }
                            }
                        }
                    })
                    .addOnFailureListener(createFailureListener("There was a problem getting the activation hint!"));
        }

        void onDisconnected() {
            Log.d(TAG, "onDisconnected()");

            Constants.signedIn = false;
            mRealTimeMultiplayerClient = null;
            mInvitationsClient = null;

            switchToMainScreen();
        }

        void signOut() {
            Log.d(TAG, "signOut()");

            mGoogleSignInClient.signOut().addOnCompleteListener((Activity) context,
                    new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {

                            if (task.isSuccessful()) {
                                Log.d(TAG, "signOut(): success");
                            } else {
                                handleException(task.getException(), "signOut() failed!");
                            }

                            onDisconnected();
                        }
                    });
        }


        // Show the waiting room UI to track the progress of other players as they enter the
        // room and get connected.
        private void showWaitingRoom(Room room) {
            // minimum number of players required for our game
            // For simplicity, we require everyone to join the game before we start it
            // (this is signaled by Integer.MAX_VALUE).
            final int MIN_PLAYERS = Integer.MAX_VALUE;
            mRealTimeMultiplayerClient.getWaitingRoomIntent(room, MIN_PLAYERS)
                    .addOnSuccessListener(new OnSuccessListener<Intent>() {
                        @Override
                        public void onSuccess(Intent intent) {
                            // show waiting room UI
                            ((Activity) context).startActivityForResult(intent, Constants.RC_WAITING_ROOM);
                        }
                    })
                    .addOnFailureListener(createFailureListener("There was a problem getting the waiting room!"));
        }

        private InvitationCallback mInvitationCallback = new InvitationCallback() {
            // Called when we get an invitation to play a game. We react by showing that to the user.
            @Override
            public void onInvitationReceived(@NonNull Invitation invitation) {
                // We got an invitation to play a game! So, store it in
                // mIncomingInvitationId
                // and show the popup on the screen.
                mIncomingInvitationId = invitation.getInvitationId();

                handshakeUI.getInvitation_questionTextView().setText(
                        invitation.getInviter().getDisplayName() + " " +
                                context.getString(R.string.is_inviting_you));

                switchToScreen(mCurScreen); // This will show the invitation popup
            }

            @Override
            public void onInvitationRemoved(@NonNull String invitationId) {

                if (mIncomingInvitationId.equals(invitationId) && mIncomingInvitationId != null) {
                    mIncomingInvitationId = null;
                    switchToScreen(mCurScreen); // This will hide the invitation popup
                }
            }
        };

        private Screen mCurScreen = Screen.SIGNOUTSCREEN;


        void switchToScreen(Screen screen) {
            mCurScreen = screen;
            Log.e("gggg", "at wait screen before");
            switch (screen) {
                case WAIT_SCREEN:
                    handshakeUI.getInviteFriends_Button().setVisibility(View.GONE);
                    handshakeUI.getSeeInvitation_Button().setVisibility(View.GONE);
                    handshakeUI.getSignOutButton().setVisibility(View.GONE);
                    handshakeUI.getSignOutTextView().setVisibility(View.GONE);
                    handshakeUI.getInviteFriendsImageView().setVisibility(View.GONE);
                    handshakeUI.getInviteFriendsTextView().setVisibility(View.GONE);
                    handshakeUI.getSeeInvitationImageView().setVisibility(View.GONE);
                    handshakeUI.getSeeInvitationTextView().setVisibility(View.GONE);
                    handshakeUI.getQuickPlayButton().setVisibility(View.GONE);
                    handshakeUI.getQuickPlayImageView().setVisibility(View.GONE);
                    handshakeUI.getQuickPlayTextView().setVisibility(View.GONE);
                    Log.e("gggg", "at wait screen");
                    handshakeUI.getWaitscreen_progressBar().setVisibility(View.VISIBLE);
                    handshakeUI.getWaitscreen_loadingTextView().setVisibility(View.VISIBLE);
                    break;

                case SIGNOUTSCREEN:
                    Log.e("gggg", "at sign out screen");
                    findViewById(R.id.multiplayer_mainPage).setVisibility(View.VISIBLE);
                    findViewById(R.id.multiplayer_handshake_overallLayout).setVisibility(View.GONE);
                    handshakeUI.getInviteFriends_Button().setVisibility(View.GONE);
                    handshakeUI.getSeeInvitation_Button().setVisibility(View.GONE);
                    handshakeUI.getSignOutButton().setVisibility(View.GONE);
                    handshakeUI.getSignOutTextView().setVisibility(View.GONE);
                    handshakeUI.getInviteFriendsImageView().setVisibility(View.GONE);
                    handshakeUI.getInviteFriendsTextView().setVisibility(View.GONE);
                    handshakeUI.getSeeInvitationImageView().setVisibility(View.GONE);
                    handshakeUI.getSeeInvitationTextView().setVisibility(View.GONE);
                    handshakeUI.getQuickPlayButton().setVisibility(View.GONE);
                    handshakeUI.getQuickPlayImageView().setVisibility(View.GONE);
                    handshakeUI.getQuickPlayTextView().setVisibility(View.GONE);

                    handshakeUI.getSignInButton().setVisibility(View.VISIBLE);
                    handshakeUI.getSignIn_helpTextView().setVisibility(View.VISIBLE);
                    handshakeUI.getSignIn_TextView().setVisibility(View.VISIBLE);
                    handshakeUI.getGoogleIconImageView().setVisibility(View.VISIBLE);
                    break;

                case SIGNIN_SCREEN:
                    Log.e("gggg", "at signed in screen");
                    findViewById(R.id.multiplayer_mainPage).setVisibility(View.GONE);
                    findViewById(R.id.multiplayer_handshake_overallLayout).setVisibility(View.VISIBLE);
                    handshakeUI.getInviteFriends_Button().setVisibility(View.VISIBLE);
                    handshakeUI.getSeeInvitation_Button().setVisibility(View.VISIBLE);
                    handshakeUI.getSignOutButton().setVisibility(View.VISIBLE);
                    handshakeUI.getSignOutTextView().setVisibility(View.VISIBLE);
                    handshakeUI.getInviteFriendsImageView().setVisibility(View.VISIBLE);
                    handshakeUI.getInviteFriendsTextView().setVisibility(View.VISIBLE);
                    handshakeUI.getSeeInvitationImageView().setVisibility(View.VISIBLE);
                    handshakeUI.getSeeInvitationTextView().setVisibility(View.VISIBLE);
                    handshakeUI.getQuickPlayButton().setVisibility(View.VISIBLE);
                    handshakeUI.getQuickPlayImageView().setVisibility(View.VISIBLE);
                    handshakeUI.getQuickPlayTextView().setVisibility(View.VISIBLE);

                    handshakeUI.getWaitscreen_progressBar().setVisibility(View.GONE);
                    handshakeUI.getWaitscreen_loadingTextView().setVisibility(View.GONE);

                    handshakeUI.getSignInButton().setVisibility(View.GONE);
                    handshakeUI.getSignIn_helpTextView().setVisibility(View.GONE);
                    handshakeUI.getSignIn_TextView().setVisibility(View.GONE);
                    handshakeUI.getGoogleIconImageView().setVisibility(View.GONE);
                    break;

                case CATEGORYSELECTION:
                    findViewById(R.id.multiplayer_handshake_overallLayout).setVisibility(View.GONE);
                    break;

                case ACTUAL_GAME:
                    findViewById(R.id.multiplayer_actualGameOverallLayout).setVisibility(View.VISIBLE);
                    break;

                case AFTER_RESTART:
                    findViewById(R.id.multiplayer_actualGameOverallLayout).setVisibility(View.GONE);
                    break;
            }

            // should we show the invitation popup?
            boolean showInvPopup;

            if (mIncomingInvitationId == null) {
                // no invitation, so no popup
                showInvPopup = false;
            } else {
                // only show invitation on main screen
                showInvPopup = (mCurScreen == Screen.SIGNIN_SCREEN);
            }

            handshakeUI.getInvitation_relativeLayout().setVisibility(showInvPopup ? View.VISIBLE : View.GONE);
        }


        /**
         * Main screen is the screen where user is presented with the button of find friends, see invitation etc
         */
        private void switchToMainScreen() {
            if (mRealTimeMultiplayerClient != null) {
                switchToScreen(Screen.SIGNIN_SCREEN);
            } else {
                switchToScreen(Screen.SIGNOUTSCREEN);
            }
        }

        // Accept the given invitation.
        private void acceptInviteToRoom(String invitationId) {
            // accept the invitation
            Log.d(TAG, "Accepting invitation: " + invitationId);

            mRoomConfig = RoomConfig.builder(mRoomUpdateCallback)
                    .setInvitationIdToAccept(invitationId)
                    .setOnMessageReceivedListener(mOnRealTimeMessageReceivedListener)
                    .setRoomStatusUpdateCallback(mRoomStatusUpdateCallback)
                    .build();

            switchToScreen(Screen.WAIT_SCREEN);
            keepScreenOn();

            mRealTimeMultiplayerClient.join(mRoomConfig)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.d(TAG, "Room Joined Successfully!");
                        }
                    });
        }

        // Handle the result of the "Select players UI" we launched when the user clicked the
        // "Invite friends" button. We react by creating a room with those players.

        void handleSelectPlayersResult(int response, Intent data) {
            if (response != Activity.RESULT_OK) {
                Log.w(TAG, "*** select players UI cancelled, " + response);
                switchToMainScreen();
                return;
            }

            Log.d(TAG, "Select players UI succeeded.");

            // get the invitee list
            final ArrayList<String> invitees = data.getStringArrayListExtra(Games.EXTRA_PLAYER_IDS);
            Log.d(TAG, "Invitee count: " + invitees.size());

            // get the automatch criteria
            Bundle autoMatchCriteria = null;
            int minAutoMatchPlayers = data.getIntExtra(Multiplayer.EXTRA_MIN_AUTOMATCH_PLAYERS, 0);
            int maxAutoMatchPlayers = data.getIntExtra(Multiplayer.EXTRA_MAX_AUTOMATCH_PLAYERS, 0);
            if (minAutoMatchPlayers > 0 || maxAutoMatchPlayers > 0) {
                autoMatchCriteria = RoomConfig.createAutoMatchCriteria(
                        minAutoMatchPlayers, maxAutoMatchPlayers, 0);
                Log.d(TAG, "Automatch criteria: " + autoMatchCriteria);
            }

            // create the room
            Log.d(TAG, "Creating room...");
            switchToScreen(Screen.WAIT_SCREEN);

            keepScreenOn();

            mRoomConfig = RoomConfig.builder(mRoomUpdateCallback)
                    .addPlayersToInvite(invitees)
                    .setOnMessageReceivedListener(mOnRealTimeMessageReceivedListener)
                    .setRoomStatusUpdateCallback(mRoomStatusUpdateCallback)
                    .setAutoMatchCriteria(autoMatchCriteria).build();
            mRealTimeMultiplayerClient.create(mRoomConfig);
            Log.d(TAG, "Room created, waiting for it to be ready...");
        }

        // Handle the result of the invitation inbox UI, where the player can pick an invitation
        // to accept. We react by accepting the selected invitation, if any.
        void handleInvitationInboxResult(int response, Intent data) {
            if (response != Activity.RESULT_OK) {
                Log.w(TAG, "*** invitation inbox UI cancelled, " + response);
                switchToMainScreen();
                return;
            }

            Log.d(TAG, "Invitation inbox UI succeeded.");
            Invitation invitation = data.getExtras().getParcelable(Multiplayer.EXTRA_INVITATION);

            // accept invitation
            if (invitation != null) {
                acceptInviteToRoom(invitation.getInvitationId());
            }
        }

        private RoomStatusUpdateCallback mRoomStatusUpdateCallback = new RoomStatusUpdateCallback() {
            // Called when we are connected to the room. We're not ready to play yet! (maybe not everybody
            // is connected yet).
            @Override
            public void onConnectedToRoom(Room room) {
                Log.d(TAG, "onConnectedToRoom.");

                //get participants and my ID:
                mParticipants = room.getParticipants();
                mMyId = room.getParticipantId(mPlayerId);

                // save room ID if its not initialized in onRoomCreated() so we can leave cleanly before the game starts.
                if (mRoomId == null) {
                    mRoomId = room.getRoomId();
                }

                // print out the list of participants (for debug purposes)
                Log.d(TAG, "Room ID: " + mRoomId);
                Log.d(TAG, "My ID " + mMyId);
                Log.d(TAG, "<< CONNECTED TO ROOM>>");
            }

            // Called when we get disconnected from the room. We return to the main screen.
            @Override
            public void onDisconnectedFromRoom(Room room) {
                mRoomId = null;
                mRoomConfig = null;
                showGameError();
            }


            // We treat most of the room update callbacks in the same way: we update our list of
            // participants and update the display. In a real game we would also have to check if that
            // change requires some action like removing the corresponding player avatar from the screen,
            // etc.
            @Override
            public void onPeerDeclined(Room room, @NonNull List<String> arg1) {
                updateRoom(room);
            }

            @Override
            public void onPeerInvitedToRoom(Room room, @NonNull List<String> arg1) {
                updateRoom(room);
            }

            @Override
            public void onP2PDisconnected(@NonNull String participant) {
            }

            @Override
            public void onP2PConnected(@NonNull String participant) {
            }

            @Override
            public void onPeerJoined(Room room, @NonNull List<String> arg1) {
                updateRoom(room);
            }

            @Override
            public void onPeerLeft(Room room, @NonNull List<String> peersWhoLeft) {
                //updateRoom(room);
            }

            @Override
            public void onRoomAutoMatching(Room room) {
                updateRoom(room);
            }

            @Override
            public void onRoomConnecting(Room room) {
                updateRoom(room);
            }

            @Override
            public void onPeersConnected(Room room, @NonNull List<String> peers) {
                updateRoom(room);
            }

            @Override
            public void onPeersDisconnected(Room room, @NonNull List<String> peers) {
                //updateRoom(room);
            }
        };

        /**
         * Start a sign in activity.  To properly handle the result, call tryHandleSignInResult from
         * your Activity's onActivityResult function
         */
        public void startSignInIntent() {
            startActivityForResult(mGoogleSignInClient.getSignInIntent(), Constants.RC_SIGN_IN);
        }


        private void updateRoom(Room room) {
            if (room != null) {
                mParticipants = room.getParticipants();
            }
        }

        // Leave the room.
        void leaveRoom() {
            Log.d(TAG, "Leaving room.");

            stopKeepingScreenOn();
            if (mRoomId != null) {
                mRealTimeMultiplayerClient.leave(mRoomConfig, mRoomId)
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                mRoomId = null;
                                mRoomConfig = null;
                            }
                        });
                switchToScreen(Screen.WAIT_SCREEN);
            } else {
                switchToMainScreen();
            }
        }

        // Show error message about game being cancelled and return to main screen.
        private void showGameError() {
            new AlertDialog.Builder(context)
                    .setMessage(context.getString(R.string.game_problem))
                    .setNeutralButton(android.R.string.ok, null).create();

            switchToMainScreen();
        }

        private RoomUpdateCallback mRoomUpdateCallback = new RoomUpdateCallback() {

            // Called when room has been created
            @Override
            public void onRoomCreated(int statusCode, Room room) {
                Log.d(TAG, "onRoomCreated(" + statusCode + ", " + room + ")");
                if (statusCode != GamesCallbackStatusCodes.OK) {
                    Log.e(TAG, "*** Error: onRoomCreated, status " + statusCode);
                    showGameError();
                    return;
                }

                // save room ID so we can leave cleanly before the game starts.
                mRoomId = room.getRoomId();

                // show the waiting room UI
                showWaitingRoom(room);
            }

            // Called when room is fully connected.
            @Override
            public void onRoomConnected(int statusCode, Room room) {
                Log.d(TAG, "onRoomConnected(" + statusCode + ", " + room + ")");
                if (statusCode != GamesCallbackStatusCodes.OK) {
                    Log.e(TAG, "*** Error: onRoomConnected, status " + statusCode);
                    showGameError();
                    return;
                }
                updateRoom(room);
            }

            @Override
            public void onJoinedRoom(int statusCode, Room room) {
                Log.d(TAG, "onJoinedRoom(" + statusCode + ", " + room + ")");
                if (statusCode != GamesCallbackStatusCodes.OK) {
                    Log.e(TAG, "*** Error: onRoomConnected, status " + statusCode);
                    showGameError();
                    return;
                }

                // show the waiting room UI
                showWaitingRoom(room);
            }

            // Called when we've successfully left the room (this happens a result of voluntarily leaving
            // via a call to leaveRoom(). If we get disconnected, we get onDisconnectedFromRoom()).
            @Override
            public void onLeftRoom(int statusCode, @NonNull String roomId) {
                // we have left the room; return to main screen.
                Log.d(TAG, "onLeftRoom, code " + statusCode);
                switchToMainScreen();
            }
        };

        private OnFailureListener createFailureListener(final String string) {
            return new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    handleException(e, string);
                }
            };
        }

        /**
         * Since a lot of the operations use tasks, we can use a common handler for whenever one fails.
         *
         * @param exception The exception to evaluate.  Will try to display a more descriptive reason for the exception.
         * @param details   Will display alongside the exception if you wish to provide more details for why the exception
         *                  happened
         */
        private void handleException(Exception exception, String details) {
            int status = 0;

            if (exception instanceof ApiException) {
                ApiException apiException = (ApiException) exception;
                status = apiException.getStatusCode();
            }

            String errorString = null;
            switch (status) {
                case GamesCallbackStatusCodes.OK:
                    break;
                case GamesClientStatusCodes.MULTIPLAYER_ERROR_NOT_TRUSTED_TESTER:
                    errorString = context.getString(R.string.status_multiplayer_error_not_trusted_tester);
                    break;
                case GamesClientStatusCodes.MATCH_ERROR_ALREADY_REMATCHED:
                    errorString = context.getString(R.string.match_error_already_rematched);
                    break;
                case GamesClientStatusCodes.NETWORK_ERROR_OPERATION_FAILED:
                    errorString = context.getString(R.string.network_error_operation_failed);
                    break;
                case GamesClientStatusCodes.INTERNAL_ERROR:
                    errorString = context.getString(R.string.internal_error);
                    break;
                case GamesClientStatusCodes.MATCH_ERROR_INACTIVE_MATCH:
                    errorString = context.getString(R.string.match_error_inactive_match);
                    break;
                case GamesClientStatusCodes.MATCH_ERROR_LOCALLY_MODIFIED:
                    errorString = context.getString(R.string.match_error_locally_modified);
                    break;
                default:
                    errorString = context.getString(R.string.unexpected_status, GamesClientStatusCodes.getStatusCodeString(status));
                    break;
            }

            if (errorString == null) {
                return;
            }

            String message = context.getString(R.string.status_exception_error, details, status, exception);

            new AlertDialog.Builder(context)
                    .setTitle("Error")
                    .setMessage(message + "\n" + errorString)
                    .setNeutralButton(android.R.string.ok, null)
                    .show();
        }

        int numberOfVotesReceived = 0;

        void receivedVote(int id) {
            multiPlayerActualGame.gameStatistic.voteCount[id]++;
        }

        /*
         * COMMUNICATIONS SECTION. Methods that implement the game's network
         * protocol.
         */

        private OnRealTimeMessageReceivedListener mOnRealTimeMessageReceivedListener = new OnRealTimeMessageReceivedListener() {
            @Override
            public void onRealTimeMessageReceived(@NonNull RealTimeMessage realTimeMessage) {
                byte[] buf = realTimeMessage.getMessageData();

                // this is the message code, used to determine what type of message received
                switch (buf[0]) {
                    case Constants.MESSAGE_FINISHVOTING:
                        if (buf[1] == multiPlayerActualGame.myUniqueID) {
                            multiPlayerActualGame.incrementScore_broadcast((byte) 1);
                        } else {
                            updateOpponentScore(buf[1], (byte) 1);
                        }
                        numberOfVotesReceived++;
                        if (numberOfVotesReceived == multiPlayerActualGame.gameParticipantSparseArray.size() - 1
                                && !multiPlayerActualGame.canVote) {
                            multiPlayerActualGame.newRoundBegin();
                        }
                        break;
                    case Constants.MESSAGE_FINISHRECORDING:
                        receivedRecordingBroadcastFromOpponent(buf[1], buf);
                        break;
                    case Constants.MESSAGE_CATEGORYSELECTION:
                        switch (buf[1]) {
                        }
                        break;

                    case Constants.MESSAGE_SPELLUSED:
                        break;
                    case Constants.MESSAGE_UPDATESCORE:
                        updateOpponentScore(buf[1], buf[2]);
                        break;

                    case Constants.MESSAGE_READY_GETREADYDIALOGSHOWN:
                        if (multiPlayerActualGame != null) {
                            multiPlayerActualGame.onBroadcastReceive_getReady();
                        }
                        break;
                    case Constants.MESSAGE_RESTARTGAME:
                        switch (buf[1]) {
                            case Constants.RESTART_YES:
                                restartYesOrNo(realTimeMessage.getSenderParticipantId(), true);
                                break;
                            case Constants.RESTART_NO:
                                restartYesOrNo(realTimeMessage.getSenderParticipantId(), false);
                                break;
                        }
                    case Constants.MESSAGE_PLAYERNAME:
                        addPlayerActualName(realTimeMessage.getSenderParticipantId(), buf);
                        break;
                }
            }
        };

        private void receivedRecordingBroadcastFromOpponent(byte id, byte[] buf) {
            byte[] actualByte = new byte[buf.length - 1];
            for (int i = 0; i < actualByte.length; i++) {
                actualByte[i] = buf[i + 1];
            }

            switch (id) {
                case 0:
                    multiPlayerActualGame.player1VoteButton.setVisibility(View.VISIBLE);
                    multiPlayerActualGame.player1Megaphone.setVisibility(View.VISIBLE);
                    multiPlayerActualGame.setToPlayByte(0, actualByte);
                    break;
                case 1:
                    multiPlayerActualGame.player2VoteButton.setVisibility(View.VISIBLE);
                    multiPlayerActualGame.player2Megaphone.setVisibility(View.VISIBLE);
                    multiPlayerActualGame.setToPlayByte(1, actualByte);
                    break;
                case 2:
                    multiPlayerActualGame.player3VoteButton.setVisibility(View.VISIBLE);
                    multiPlayerActualGame.player3Megaphone.setVisibility(View.VISIBLE);
                    multiPlayerActualGame.setToPlayByte(2, actualByte);
                    break;
            }
        }

        // put the actual in game name of the player into the hash map
        private void addPlayerActualName(String playerParticipantId, byte[] theByteArrReceived) {
            String actualName;
            if (theByteArrReceived.length == 1) {
                // means empty name
                actualName = "";
            } else {
                // dont read the first element in the byte [] cos that is message code
                byte[] nameByteArr = Arrays.copyOfRange(theByteArrReceived, 1, theByteArrReceived.length);
                actualName = new String(nameByteArr);
            }
            playerNameMap.put(playerParticipantId, actualName);
        }


        // Sets the flag to keep this screen on. It's recommended to do that during
        // the handshake when setting up a game, because if the screen turns off, the
        // game will be cancelled.
        private void keepScreenOn() {
            ((Activity) context).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // Clears the flag that keeps the screen on.
        void stopKeepingScreenOn() {
            ((Activity) context).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    class ClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.multiplayer_signIn_Button:
                    // start the sign-in flow
                    SoundPoolManager.getInstance().playSound(0);
                    Log.d(TAG, "Sign-in button clicked");
                    handshakeConnection.startSignInIntent();
                    break;

                case R.id.multiplayer_signOut_Button:
                    // user wants to sign out sign out.
                    SoundPoolManager.getInstance().playSound(0);
                    Log.d(TAG, "Sign-out button clicked");
                    handshakeConnection.signOut();
                    handshakeConnection.switchToScreen(Screen.SIGNOUTSCREEN);
                    break;

                case R.id.multiplayer_quickPlay_Button:
                    SoundPoolManager.getInstance().playSound(0);
                    handshakeConnection.switchToScreen(Screen.WAIT_SCREEN);
                    startQuickGame();
                    break;

                case R.id.multiplayer_inviteFriends_Button:
                    SoundPoolManager.getInstance().playSound(0);
                    handshakeConnection.switchToScreen(Screen.WAIT_SCREEN);

                    // show list of invitable players
                    handshakeConnection.mRealTimeMultiplayerClient.getSelectOpponentsIntent(1, 7).addOnSuccessListener(
                            new OnSuccessListener<Intent>() {
                                @Override
                                public void onSuccess(Intent intent) {
                                    startActivityForResult(intent, Constants.RC_SELECT_PLAYERS);
                                }
                            }
                    ).addOnFailureListener(handshakeConnection.createFailureListener("There was a problem selecting opponents."));
                    break;

                case R.id.multiplayer_seeInvitation_Button:
                    SoundPoolManager.getInstance().playSound(0);
                    handshakeConnection.switchToScreen(Screen.WAIT_SCREEN);

                    // show list of pending invitations
                    handshakeConnection.mInvitationsClient.getInvitationInboxIntent().addOnSuccessListener(
                            new OnSuccessListener<Intent>() {
                                @Override
                                public void onSuccess(Intent intent) {
                                    startActivityForResult(intent, Constants.RC_INVITATION_INBOX);
                                }
                            }
                    ).addOnFailureListener(handshakeConnection.createFailureListener("There was a problem getting the inbox."));
                    break;

                case R.id.multiplayer_invitationAcceptButton:
                    SoundPoolManager.getInstance().playSound(0);
                    // user wants to accept the invitation shown on the invitation popup
                    // (the one we got through the OnInvitationReceivedListener).
                    handshakeConnection.acceptInviteToRoom(handshakeConnection.mIncomingInvitationId);
                    handshakeConnection.mIncomingInvitationId = null;
                    break;

                case R.id.multiplayer_backbutton:
                    SoundPoolManager.getInstance().playSound(2);
                    onBackPressed();
                    break;
            }
        }
    }

    enum Screen {
        // used for switching screens
        WAIT_SCREEN, SIGNIN_SCREEN, SIGNOUTSCREEN, ACTUAL_GAME, CATEGORYSELECTION, AFTER_RESTART;
    }
}


class MultiPlayerActualGame implements View.OnClickListener {
    // input when that button is clicked.
    private SparseArray<String> keyValues = new SparseArray<>();
    private boolean goingToExit = false; // change to true if user clicked exit button in pause menu
    boolean gameHasEnded = false; // set to true when game ends
    private Context context;
    private Activity aContext;
    private MultiPlayerGame.HandshakeConnection handshakeConnection;
    private PriorityQueue<GameParticipant> gameParticipantsPriorityQueue;
    // int is id to identify the gameparticipant
    // this is a hashmap used to change the priorityqueue priority
    SparseArray<GameParticipant> gameParticipantSparseArray;
    byte myUniqueID; // the unique id representing MYSELF, to be sent in broadcast message
    private int numberOfPlayersOnReady = 0;
    private TextView topText, bottomText; // for get ready dialog
    private boolean gameStarted = false;
    private MultiPlayerGame multiPlayerGame;
    TextView restartButton;
    private StringBuffer editTextString = new StringBuffer(); // for keyboard
    private LinearLayout getReadyContainer;
    private static final String TAG = "gggg";
    private boolean hasNewQuestions = false; // used for seemore edittext typing to match with text
    GameStatistic gameStatistic;
    private AnswerDeck answerDeck;
    List<String> cardsInHand = new ArrayList<>();
    TextView card1TextView, card2TextView, card3TextView, card4TextView, card5TextView, questionTextView;
    ImageView player1Megaphone, player2Megaphone, player3Megaphone, player1VoteButton, player2VoteButton, player3VoteButton, card1Recorder, card2Recorder, card3Recorder, card4Recorder, card5Recorder, card1, card2, card3, card4, card5;
    private boolean isNowRecording = false;
    boolean canVote = true;
    EditText card1_editText, card2_editText, card3_editText, card4_editText, card5_editText;
    private TextView player1ScoreTextView, player2ScoreTextView, player3ScoreTextView;
    private int cardSelected = 1;
    String permQn;
    byte[] p1_toPlayByte, p2_toPlayByte, p3_toPlayByte;
    DatabaseReference mDatabase;
    MediaRecorder mRecorder;
    MediaPlayer mPlayer;
    String mFileName = null;
    StorageReference mStorage;
    Uri uri;
    String sentFileNameToBroadcast;


    public MultiPlayerActualGame(final Context context, MultiPlayerGame.HandshakeConnection handshakeConnection) {
        this.context = context;
        this.aContext = (Activity) context;
        this.multiPlayerGame = (MultiPlayerGame) context;
        this.handshakeConnection = handshakeConnection;
        multiPlayerGame.isAppRunning = true;

        initializeGameParticipants();
        startingDraw5Cards();
        getNewQuestionDisplayed();
        initializeVariables();
        initializeListener();
        permQn = questionTextView.getText().toString();

        showGetReadyDialog();

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!gameStarted) {
                    gameStarted = true;
                    closeGetReadyDialog();

                }
            }
        }, 4000);
    }

    void setToPlayByte(int id, byte[] actual) {
        switch (id) {
            case 0:
                p1_toPlayByte = actual;
                break;
            case 1:
                p2_toPlayByte = actual;
                break;
            case 2:
                p3_toPlayByte = actual;
                break;
        }
    }

    void initializeVariables() {
        player1Megaphone = ((Activity) context).findViewById(R.id.player1Megaphone);
        player2Megaphone = ((Activity) context).findViewById(R.id.player2Megaphone);
        player3Megaphone = ((Activity) context).findViewById(R.id.player3Megaphone);
        player1VoteButton = ((Activity) context).findViewById(R.id.player1VoteButton);
        player2VoteButton = ((Activity) context).findViewById(R.id.player2VoteButton);
        player3VoteButton = ((Activity) context).findViewById(R.id.player3VoteButton);
        card1 = ((Activity) context).findViewById(R.id.card1);
        card2 = ((Activity) context).findViewById(R.id.card2);
        card3 = ((Activity) context).findViewById(R.id.card3);
        card4 = ((Activity) context).findViewById(R.id.card4);
        card5 = ((Activity) context).findViewById(R.id.card5);
        card1_editText = ((Activity) context).findViewById(R.id.edittext_card1);
        card2_editText = ((Activity) context).findViewById(R.id.edittext_card2);
        card3_editText = ((Activity) context).findViewById(R.id.edittext_card3);
        card4_editText = ((Activity) context).findViewById(R.id.edittext_card4);
        card5_editText = ((Activity) context).findViewById(R.id.edittext_card5);
        player1ScoreTextView = ((Activity) context).findViewById(R.id.player1_scoreText);
        player2ScoreTextView = ((Activity) context).findViewById(R.id.player2_scoreText);
        player3ScoreTextView = ((Activity) context).findViewById(R.id.player3_scoreText);

        card1Recorder = ((Activity) context).findViewById(R.id.card1_RecordButton);
        card2Recorder = ((Activity) context).findViewById(R.id.card2_RecordButton);
        card3Recorder = ((Activity) context).findViewById(R.id.card3_RecordButton);
        card4Recorder = ((Activity) context).findViewById(R.id.card4_RecordButton);
        card5Recorder = ((Activity) context).findViewById(R.id.card5_RecordButton);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        mStorage = FirebaseStorage.getInstance().getReference();
        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName += "/recorded_audio.mp3";
    }


    void hideAllMegaphonesAndVotes() {
        player1Megaphone.setVisibility(View.GONE);
        player2Megaphone.setVisibility(View.GONE);
        player3Megaphone.setVisibility(View.GONE);
        player1VoteButton.setVisibility(View.GONE);
        player2VoteButton.setVisibility(View.GONE);
        player3VoteButton.setVisibility(View.GONE);
    }

    void cardSelected(int cardNumber) {
        hideAllRecorders();
        switch (cardNumber) {
            case 1:
                card1Recorder.setVisibility(View.VISIBLE);
                card1Recorder.bringToFront();
                if (card1TextView.getText().toString().equalsIgnoreCase("Wild Card")) {
                    card1_editText.setVisibility(View.VISIBLE);
                    card1_editText.requestFocus();
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    card1_editText.addTextChangedListener(new TextWatcher() {

                        public void onTextChanged(CharSequence s, int start, int before,
                                                  int count) {
                            if (!s.equals("")) {
                                card1TextView.setText(s);
                            }
                        }


                        public void beforeTextChanged(CharSequence s, int start, int count,
                                                      int after) {

                        }

                        public void afterTextChanged(Editable s) {

                        }
                    });
                } else {
                    questionTextView.setText(getFinalAnswer(permQn, card1TextView.getText().toString()));
                }
                break;
            case 2:
                card2Recorder.setVisibility(View.VISIBLE);
                card2Recorder.bringToFront();

                if (card1TextView.getText().toString().equalsIgnoreCase("Wild Card")) {
                    card1_editText.setVisibility(View.VISIBLE);
                    card1_editText.requestFocus();
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    card1_editText.addTextChangedListener(new TextWatcher() {

                        public void onTextChanged(CharSequence s, int start, int before,
                                                  int count) {
                            if (!s.equals("")) {
                                card1TextView.setText(s);
                            }
                        }


                        public void beforeTextChanged(CharSequence s, int start, int count,
                                                      int after) {

                        }

                        public void afterTextChanged(Editable s) {

                        }
                    });
                } else {
                    questionTextView.setText(getFinalAnswer(permQn, card2TextView.getText().toString()));
                }
                break;
            case 3:
                card3Recorder.setVisibility(View.VISIBLE);
                card3Recorder.bringToFront();

                if (card1TextView.getText().toString().equalsIgnoreCase("Wild Card")) {
                    card1_editText.setVisibility(View.VISIBLE);
                    card1_editText.requestFocus();
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    card1_editText.addTextChangedListener(new TextWatcher() {

                        public void onTextChanged(CharSequence s, int start, int before,
                                                  int count) {
                            if (!s.equals("")) {
                                card1TextView.setText(s);
                            }
                        }


                        public void beforeTextChanged(CharSequence s, int start, int count,
                                                      int after) {

                        }

                        public void afterTextChanged(Editable s) {

                        }
                    });
                } else {
                    questionTextView.setText(getFinalAnswer(permQn, card3TextView.getText().toString()));
                }
                break;
            case 4:
                card4Recorder.setVisibility(View.VISIBLE);
                card4Recorder.bringToFront();

                if (card1TextView.getText().toString().equalsIgnoreCase("Wild Card")) {
                    card1_editText.setVisibility(View.VISIBLE);
                    card1_editText.requestFocus();
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    card1_editText.addTextChangedListener(new TextWatcher() {

                        public void onTextChanged(CharSequence s, int start, int before,
                                                  int count) {
                            if (!s.equals("")) {
                                card1TextView.setText(s);
                            }
                        }


                        public void beforeTextChanged(CharSequence s, int start, int count,
                                                      int after) {

                        }

                        public void afterTextChanged(Editable s) {

                        }
                    });
                } else {

                    questionTextView.setText(getFinalAnswer(permQn, card4TextView.getText().toString()));
                }
                break;
            case 5:
                card5Recorder.setVisibility(View.VISIBLE);
                card5Recorder.bringToFront();

                if (card1TextView.getText().toString().equalsIgnoreCase("Wild Card")) {
                    card1_editText.setVisibility(View.VISIBLE);
                    card1_editText.requestFocus();
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    card1_editText.addTextChangedListener(new TextWatcher() {

                        public void onTextChanged(CharSequence s, int start, int before,
                                                  int count) {
                            if (!s.equals("")) {
                                card1TextView.setText(s);
                            }
                        }

                        public void beforeTextChanged(CharSequence s, int start, int count,
                                                      int after) {

                        }

                        public void afterTextChanged(Editable s) {

                        }
                    });
                } else {
                    questionTextView.setText(getFinalAnswer(permQn, card5TextView.getText().toString()));
                }
                break;
        }
    }


    void startPlaying(int id) {
        mPlayer = new MediaPlayer();
        switch (id) {
            case 0: {
                String s = new String(p1_toPlayByte);
                String SDCardRoot = Environment.getExternalStorageDirectory()
                        .toString();
                downloadFile(s, "my_file.mp3",
                        SDCardRoot + "/AppFolder");
                break;
            }
            case 1: {
                String s = new String(p2_toPlayByte);
                String SDCardRoot = Environment.getExternalStorageDirectory()
                        .toString();
                downloadFile(s, "my_file.mp3",
                        SDCardRoot + "/AppFolder");
                break;
            }
            case 2: {
                String s = new String(p3_toPlayByte);
                String SDCardRoot = Environment.getExternalStorageDirectory()
                        .toString();
                downloadFile(s, "my_file.mp3",
                        SDCardRoot + "/AppFolder");
                break;
            }
        }
    }

    void downloadFile(String dwnload_file_path, String fileName,
                      String pathToSave) {
        int downloadedSize = 0;
        int totalSize = 0;

        try {
            URL url = new URL(dwnload_file_path);
            HttpURLConnection urlConnection = (HttpURLConnection) url
                    .openConnection();

            urlConnection.setRequestMethod("POST");
            urlConnection.setDoOutput(true);

            // connect
            urlConnection.connect();

            File myDir;
            myDir = new File(pathToSave);
            myDir.mkdirs();

            // create a new file, to save the downloaded file

            String mFileName = fileName;
            File file = new File(myDir, mFileName);

            FileOutputStream fileOutput = new FileOutputStream(file);

            // Stream used for reading the data from the internet
            InputStream inputStream = urlConnection.getInputStream();

            // this is the total size of the file which we are downloading
            totalSize = urlConnection.getContentLength();

            // create a buffer...
            byte[] buffer = new byte[1024];
            int bufferLength = 0;

            while ((bufferLength = inputStream.read(buffer)) > 0) {
                fileOutput.write(buffer, 0, bufferLength);
                downloadedSize += bufferLength;
            }
            // close the output stream when complete //
            fileOutput.close();

        } catch (final MalformedURLException e) {
            // showError("Error : MalformedURLException " + e);
            e.printStackTrace();
        } catch (final IOException e) {
            // showError("Error : IOException " + e);
            e.printStackTrace();
        } catch (final Exception e) {
            // showError("Error : Please check your internet connection " + e);
        }
    }

    private void uploadAudio() {
        final StorageReference filepath = mStorage.child("Audio").child("new_audio.3gp");
        uri = Uri.fromFile(new File(mFileName));
        filepath.putFile(uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                filepath.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri url) {
                        uri = url;
                        sentFileNameToBroadcast = url.toString();
                        sendBroadcast_finishRecording();

                    }
                });
            }

            ;
        });

    }

    String getFinalAnswer(String qn, String ans) {
        ans = ans.toUpperCase();
        int startPosition;
        int endPosition;
        String finalAnswer;
        String[] splitQn = new String[2];
        startPosition = qn.indexOf('_');
        endPosition = startPosition + ans.length() - 1;
        if (qn.charAt(qn.length() - 1) == '_') {
            finalAnswer = qn.substring(0, qn.length() - 4);
        } else {
            splitQn = qn.split("____");
            finalAnswer = splitQn[0] + ans + splitQn[1];
        }
        return finalAnswer;
    }

    void hideAllRecorders() {
        card1Recorder.setVisibility(View.INVISIBLE);
        card2Recorder.setVisibility(View.INVISIBLE);
        card3Recorder.setVisibility(View.INVISIBLE);
        card4Recorder.setVisibility(View.INVISIBLE);
        card5Recorder.setVisibility(View.INVISIBLE);
    }

    private void startingDraw5Cards() {
        int totalNumberOfPlayers = gameParticipantsPriorityQueue.size();
        gameStatistic = new GameStatistic();
        answerDeck = new AnswerDeck();

        for (int j = 0; j < totalNumberOfPlayers; j++) {
            gameStatistic.players[j] = gameParticipantSparseArray.get(j);
        }
        initialDraw(answerDeck);
    }

    void newRoundBegin() {
        getNewQuestionDisplayed();
        cardsInHand.set(cardSelected - 1, answerDeck.getNewCard());
        hideAllRecorders();
        hideAllMegaphonesAndVotes();
        canVote = true;
        permQn = questionTextView.getText().toString();
    }

    void sendBroadcast_vote(byte id) {
        canVote = false;
        RealTimeMultiplayerMessage realTimeMultiplayerMessage = new RealTimeMultiplayerMessage((byte) Constants.MESSAGE_FINISHVOTING, id);
        realTimeMultiplayerMessage.setData(id);

        // Send to every other participant.
        for (Participant p : handshakeConnection.mParticipants) {
            if (p.getParticipantId().equals(handshakeConnection.mMyId)) {
                // this is me, hence dont send
                continue;
            }

            if (p.getStatus() != Participant.STATUS_JOINED) {
                continue;
            }

            handshakeConnection.mRealTimeMultiplayerClient.sendReliableMessage(realTimeMultiplayerMessage.getMessage(),
                    handshakeConnection.mRoomId, p.getParticipantId(), new RealTimeMultiplayerClient.ReliableMessageSentCallback() {
                        @Override
                        public void onRealTimeMessageSent(int statusCode, int tokenId, String recipientParticipantId) {
                            synchronized (this) {
                            }
                        }
                    });
        }
    }

    void sendBroadcast_finishRecording() {
        RealTimeMultiplayerMessage realTimeMultiplayerMessage = new RealTimeMultiplayerMessage((byte) Constants.MESSAGE_FINISHRECORDING, myUniqueID
                , sentFileNameToBroadcast.getBytes());

        // Send to every other participant.
        for (Participant p : handshakeConnection.mParticipants) {
            if (p.getParticipantId().equals(handshakeConnection.mMyId)) {
                // this is me, hence dont send
                continue;
            }

            if (p.getStatus() != Participant.STATUS_JOINED) {
                continue;
            }

            handshakeConnection.mRealTimeMultiplayerClient.sendReliableMessage(realTimeMultiplayerMessage.getMessage(),
                    handshakeConnection.mRoomId, p.getParticipantId(), new RealTimeMultiplayerClient.ReliableMessageSentCallback() {
                        @Override
                        public void onRealTimeMessageSent(int statusCode, int tokenId, String recipientParticipantId) {
                            synchronized (this) {
                            }
                        }
                    });
        }
    }

    void initialDraw(AnswerDeck answerDeck) {
        int count = 5;
        while (count > 0) {
            cardsInHand.add(answerDeck.getNewCard());
            count--;
        }

        card1TextView = ((Activity) context).findViewById(R.id.card1TextView);
        card2TextView = ((Activity) context).findViewById(R.id.card2TextView);
        card3TextView = ((Activity) context).findViewById(R.id.card3TextView);
        card4TextView = ((Activity) context).findViewById(R.id.card4TextView);
        card5TextView = ((Activity) context).findViewById(R.id.card5TextView);
        questionTextView = ((Activity) context).findViewById(R.id.questionText);
        card1TextView.setText(cardsInHand.get(0));
        card2TextView.setText(cardsInHand.get(1));
        card3TextView.setText(cardsInHand.get(2));
        card4TextView.setText(cardsInHand.get(3));
        card5TextView.setText(cardsInHand.get(4));
    }

    void initializeListener() {
        card1.setOnClickListener(this);
        card2.setOnClickListener(this);
        card3.setOnClickListener(this);
        card4.setOnClickListener(this);
        card5.setOnClickListener(this);

        player1Megaphone.setOnClickListener(this);
        player2Megaphone.setOnClickListener(this);
        player3Megaphone.setOnClickListener(this);
        player1VoteButton.setOnClickListener(this);
        player2VoteButton.setOnClickListener(this);
        player3VoteButton.setOnClickListener(this);

        card1Recorder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isNowRecording) {
                    // stop recording
                    card1Recorder.setImageResource(R.drawable.record);
                    stopRecording();
                    try {
                        mPlayer = new MediaPlayer();
                        mPlayer.setDataSource(mFileName);
                        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        mPlayer.prepare(); //don't use prepareAsync for mp3 playback
                        mPlayer.start();
                    } catch (Exception e) {
                    }
                    isNowRecording = !isNowRecording;
                } else {
                    // start recording
                    AlertDialog alertDialog = new AlertDialog.Builder(context).create();
                    alertDialog.setTitle("Record your voice now?");
                    alertDialog.setMessage("Select \"" + card1TextView.getText().toString() + "\" and record your voice now?");
                    alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Yes",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    cardSelected = 1;
                                    card1Recorder.setImageResource(R.drawable.stop);
                                    startRecording();
                                    isNowRecording = !isNowRecording;
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "No",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                }
            }
        });

        card2Recorder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isNowRecording) {
                    // stop recording
                    card2Recorder.setImageResource(R.drawable.record);
                    stopRecording();
                    isNowRecording = !isNowRecording;
                    try {
                        mPlayer = new MediaPlayer();
                        mPlayer.setDataSource(mFileName);
                        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        mPlayer.prepare(); //don't use prepareAsync for mp3 playback
                        mPlayer.start();
                    } catch (Exception e) {
                    }
                    isNowRecording = !isNowRecording;

                } else {
                    // start recording
                    AlertDialog alertDialog = new AlertDialog.Builder(context).create();
                    alertDialog.setTitle("Record your voice now?");
                    alertDialog.setMessage("Select \"" + card2TextView.getText().toString() + "\" and record your voice now?");
                    alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Yes",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    cardSelected = 2;
                                    card2Recorder.setImageResource(R.drawable.stop);
                                    startRecording();
                                    isNowRecording = !isNowRecording;
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "No",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                }
            }
        });
        card3Recorder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isNowRecording) {
                    // stop recording
                    card3Recorder.setImageResource(R.drawable.record);
                    stopRecording();
                    try {
                        mPlayer = new MediaPlayer();
                        mPlayer.setDataSource(mFileName);
                        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        mPlayer.prepare(); //don't use prepareAsync for mp3 playback
                        mPlayer.start();
                    } catch (Exception e) {
                    }
                    isNowRecording = !isNowRecording;
                } else {
                    // start recording
                    AlertDialog alertDialog = new AlertDialog.Builder(context).create();
                    alertDialog.setTitle("Record your voice now?");
                    alertDialog.setMessage("Select \"" + card3TextView.getText().toString() + "\" and record your voice now?");
                    alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Yes",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    cardSelected = 3;
                                    card3Recorder.setImageResource(R.drawable.stop);
                                    startRecording();
                                    isNowRecording = !isNowRecording;
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "No",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                }
            }
        });
        card4Recorder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isNowRecording) {
                    // stop recording
                    card4Recorder.setImageResource(R.drawable.record);
                    stopRecording();
                    try {
                        mPlayer = new MediaPlayer();
                        mPlayer.setDataSource(mFileName);
                        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        mPlayer.prepare(); //don't use prepareAsync for mp3 playback
                        mPlayer.start();
                    } catch (Exception e) {
                    }
                    isNowRecording = !isNowRecording;
                } else {
                    // start recording
                    AlertDialog alertDialog = new AlertDialog.Builder(context).create();
                    alertDialog.setTitle("Record your voice now?");
                    alertDialog.setMessage("Select \"" + card4TextView.getText().toString() + "\" and record your voice now?");
                    alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Yes",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    cardSelected = 4;
                                    card4Recorder.setImageResource(R.drawable.stop);
                                    startRecording();
                                    isNowRecording = !isNowRecording;
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "No",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                }
            }
        });
        card5Recorder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isNowRecording) {
                    // stop recording
                    card5Recorder.setImageResource(R.drawable.record);
                    stopRecording();
                    try {
                        mPlayer = new MediaPlayer();
                        mPlayer.setDataSource(mFileName);
                        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        mPlayer.prepare(); //don't use prepareAsync for mp3 playback
                        mPlayer.start();
                    } catch (Exception e) {
                    }
                    isNowRecording = !isNowRecording;
                } else {
                    // start recording
                    AlertDialog alertDialog = new AlertDialog.Builder(context).create();
                    alertDialog.setTitle("Record your voice now?");
                    alertDialog.setMessage("Select \"" + card5TextView.getText().toString() + "\" and record your voice now?");
                    alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Yes",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    cardSelected = 5;
                                    card5Recorder.setImageResource(R.drawable.stop);
                                    startRecording();
                                    isNowRecording = !isNowRecording;
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "No",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                }
            }
        });
    }

    void getNewQuestionDisplayed() {
        questionTextView.setText(gameStatistic.getQuestionDeck().getNextQuestion());
    }

    void startRecording() {

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSamplingRate(16000);
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        mRecorder.setOutputFile(mFileName);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        try {
            mRecorder.prepare();
        } catch (IOException e) {
        }

        mRecorder.start();
    }

    //convert short to byte
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;

    }

    private void stopRecording() {
        // stops the recording activity
        mRecorder.stop();

        uploadAudio();
    }

    // after get ready dialog has appear, i am ready, hence broadcast to others. This is the start animation
    private void broadcast_tellOthersIAmReady() {
        Log.e("gggg", "broadcasting to others i am ready");

        RealTimeMultiplayerMessage realTimeMultiplayerMessage = new RealTimeMultiplayerMessage(Constants.MESSAGE_READY_GETREADYDIALOGSHOWN);
        realTimeMultiplayerMessage.setData((byte) Constants.IS_READY);

        // Send to every other participant.
        for (Participant p : handshakeConnection.mParticipants) {
            if (p.getParticipantId().equals(handshakeConnection.mMyId)) {
                // this is me, hence dont send
                continue;
            }

            if (p.getStatus() != Participant.STATUS_JOINED) {
                continue;
            }

            handshakeConnection.mRealTimeMultiplayerClient.sendReliableMessage(realTimeMultiplayerMessage.getMessage(),
                    handshakeConnection.mRoomId, p.getParticipantId(), new RealTimeMultiplayerClient.ReliableMessageSentCallback() {
                        @Override
                        public void onRealTimeMessageSent(int statusCode, int tokenId, String recipientParticipantId) {
                            synchronized (this) {
                                onBroadcastReceive_getReady();
                            }
                        }
                    });
        }
    }

    // this is after the get ready animation left to right and bottom to down, then tell ppl i am ready.
    public void onBroadcastReceive_getReady() {
        numberOfPlayersOnReady++;
        if (numberOfPlayersOnReady == handshakeConnection.mParticipants.size()) {
            Log.e("gggg", "all ready oh yeah");


            // start timer frst before closing dialog to prevent delay
            gameStarted = true;

            YoYo.with(Techniques.SlideOutUp)
                    .duration(400)
                    .playOn(topText);

            YoYo.with(Techniques.SlideOutDown)
                    .duration(400).withListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    closeGetReadyDialog();
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            }).playOn(bottomText);
        }
    }

    private void closeGetReadyDialog() {
        if (getReadyContainer != null) {
            ((ConstraintLayout) aContext.findViewById(R.id.multiplayer_actualGameOverallLayout)).removeView(getReadyContainer);
        }
    }

    private void showGetReadyDialog() {
        LayoutInflater inflater = ((Activity) context).getLayoutInflater();
        View view = inflater.inflate(R.layout.getready_layout, null);
        getReadyContainer = (LinearLayout) view;


        ((ConstraintLayout) aContext.findViewById(R.id.multiplayer_actualGameOverallLayout)).addView(getReadyContainer,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        topText = view.findViewById(R.id.getready_topText);
        bottomText = view.findViewById(R.id.getready_bottomText);
        bottomText.setText("All the best!");

        YoYo.with(Techniques.SlideInLeft)
                .duration(700)
                .playOn(topText);

        YoYo.with(Techniques.SlideInRight)
                .duration(700).withListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!gameStarted) {
                            broadcast_tellOthersIAmReady();
                        }
//                        multiPlayerGame.releaseMediaPlayer();
//                        multiPlayerGame.playMediaPlayer(1); // play background music
                    }
                }, 1400);
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        }).playOn(bottomText);
    }


    private void initializeGameParticipants() {
        this.gameParticipantsPriorityQueue = new PriorityQueue<>();
        this.gameParticipantSparseArray = new SparseArray<>();

        for (int i = 0; i < handshakeConnection.mParticipants.size(); i++) {
            GameParticipant gameParticipant = new GameParticipant(handshakeConnection.playerNameMap.get(
                    handshakeConnection.mParticipants.get(i).getParticipantId()),
                    0, (byte) i, handshakeConnection.mParticipants.get(i).getParticipantId());
            gameParticipantSparseArray.put(i, gameParticipant);
            gameParticipantsPriorityQueue.add(gameParticipant);
            if (gameParticipant.getParticipantId().equals(handshakeConnection.mMyId)) {
                // this is me
                myUniqueID = gameParticipant.getUniqueId();
            }
        }
    }

    // id is the unique identifier to identify this game participant
    // this will result in all the participant scores to be looped through the for loop
    private void updateGameParticipantsScore(byte id) {
    }

    // opponent score has increases, update his score in ranking
    void updateOpponentScore(byte id, byte scoreToAdd) {
        updateGameParticipantsScore(id);
        gameStatistic.voteCount[id]++;

        player1ScoreTextView.setText("" + gameStatistic.voteCount[0]);
        player2ScoreTextView.setText("" + gameStatistic.voteCount[1]);
        player3ScoreTextView.setText("" + gameStatistic.voteCount[2]);
    }

    private String formatTime(long ms) {
        long s = ms / 1000; // convert from ms to s
        long mins = s / 60;
        long seconds = s % 60;

        if (seconds < 10) {
            return "" + mins + ":" + "0" + seconds;
        } else {
            return "" + mins + ":" + seconds;
        }
    }

    void onBackPressed() {
        handshakeConnection.switchToScreen(MultiPlayerGame.Screen.AFTER_RESTART);
    }

    // my score is incremented. Broadcast to everyone
    void incrementScore_broadcast(byte scoreToIncrement) {
        RealTimeMultiplayerMessage realTimeMultiplayerMessage = new RealTimeMultiplayerMessage(Constants.MESSAGE_UPDATESCORE);
        realTimeMultiplayerMessage.setData(myUniqueID, scoreToIncrement);

        // Send to every other participant.
        for (Participant p : handshakeConnection.mParticipants) {
            if (p.getParticipantId().equals(handshakeConnection.mMyId)) {
                // this is me, hence dont send
                continue;
            }

            if (p.getStatus() != Participant.STATUS_JOINED) {
                continue;
            }

            handshakeConnection.mRealTimeMultiplayerClient.sendReliableMessage(realTimeMultiplayerMessage.getMessage(),
                    handshakeConnection.mRoomId, p.getParticipantId(), new RealTimeMultiplayerClient.ReliableMessageSentCallback() {
                        @Override
                        public void onRealTimeMessageSent(int statusCode, int tokenId, String recipientParticipantId) {

                        }
                    });
        }
    }

    void dismissDialogEnd() {
    }

    void onRestartChanges() {
        dismissDialogEnd();
        gameHasEnded = false;
        gameStarted = false;
        numberOfPlayersOnReady = 0;

        for (int i = 0; i < gameParticipantSparseArray.size(); i++) {
            gameParticipantSparseArray.get(i).clearScore();
        }
        // call one time to update ranking display
        updateGameParticipantsScore((byte) 0);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.player1VoteButton:
                if (canVote) {
                    sendBroadcast_vote((byte) 1);
                    gameStatistic.voteCount[0]++;
                    player1ScoreTextView.setText("" + gameStatistic.voteCount[0]);
                    canVote = false;
                }
                break;

            case R.id.player2VoteButton:
                if (canVote) {
                    sendBroadcast_vote((byte) 2);
                    gameStatistic.voteCount[1]++;
                    player2ScoreTextView.setText("" + gameStatistic.voteCount[1]);
                    canVote = false;
                }
                break;

            case R.id.player3VoteButton:
                if (canVote) {
                    sendBroadcast_vote((byte) 3);
                    gameStatistic.voteCount[2]++;
                    player3ScoreTextView.setText("" + gameStatistic.voteCount[2]);
                    canVote = false;
                }
                break;

            case R.id.card1:
                cardSelected(1);
                break;
            case R.id.card2:
                cardSelected(2);
                break;
            case R.id.card3:
                cardSelected(3);
                break;
            case R.id.card4:
                cardSelected(4);
                break;
            case R.id.card5:
                cardSelected(5);
                break;

            case R.id.player1Megaphone:
                startPlaying(0);
                break;
            case R.id.player2Megaphone:

                startPlaying(1);
                break;
            case R.id.player3Megaphone:

                startPlaying(2);
                break;
        }
    }
}

class GameParticipant implements Comparable<GameParticipant> {
    private String name;
    private int score;
    private byte uniqueId; // id is unique identifier to identify the participant based on send message byte array, a number of 0 - 7
    // participant id defers from id. Participant id is the id returns by google play service Player.getParticipantId()
    private String participantId;
    int voteNum;

    public GameParticipant(String name, int score, byte uniqueId, String participantId) {
        if (name == null) {
            this.name = "";
        } else {
            this.name = name;
        }
        this.score = score;
        this.uniqueId = uniqueId;
        this.participantId = participantId;
    }

    public String getParticipantId() {
        return this.participantId;
    }

    public byte getUniqueId() {
        return this.uniqueId;
    }

    public void incrementScore(byte scoreToAdd) {
        this.score += scoreToAdd;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    // for used in restart
    public void clearScore() {
        this.score = 0;
    }

    @Override
    public String toString() {
        return name + "  (" + score + ")";
    }

    // compare base on whose score is higher
    @Override
    public int compareTo(@NonNull GameParticipant o) {
        return o.score - this.score;
    }

    void increasePlayerScore() {
        this.score = score + 1;
    }

    void setVote(int vote) {
        this.voteNum = vote;
    }

    int getVote() {
        return voteNum;
    }
}

class RealTimeMultiplayerMessage {
    byte[] msgByte;

    public RealTimeMultiplayerMessage(char index0) {
        msgByte = new byte[7];
        msgByte[0] = (byte) index0;
    }

    public RealTimeMultiplayerMessage(byte value, byte myId, byte[] arr) {
        byte[] combined = new byte[arr.length + 2];
        combined[0] = value;
        combined[1] = myId;
        for (int i = 2; i < combined.length; i++) {
            combined[i] = arr[i - 2];
        }

        this.msgByte = combined;
    }

    public RealTimeMultiplayerMessage(byte value, byte myId) {
        msgByte = new byte[7];
        msgByte[0] = (byte) value;
        msgByte[1] = (byte) myId;
    }

    public void setData(byte index1) {
        msgByte[1] = index1;
    }

    public void setData(byte index1, byte index2) {
        msgByte[1] = index1;
        msgByte[2] = index2;
    }

    public byte[] getMessage() {
        return msgByte;
    }
}

class QuestionDeck {
    //stack 1
    String[] qns1 = {"Eh, tonight we go ____ find chiobus", "Wah your new shoes look sibei ____ sia", "Sian, today need to go ____ again", "My parents very kiasu lah, keep sending me to ____", "He sibei ____, everytime never come for meetings one", "Bro I going NS soon, cannot ____", "Eh I heard downstairs got ____ leh", "Sorry cher, I never come school cos ____", "Want ____ for your birthday?", "I like to ____ in my free time"};
    String[] ans1 = {"siamdiu", "tekong", "library", "kopitiam", "exercise", "istana", "tan tock seng", "bukit timah hill", "geylang", "book in", "atas", "bling bling", "chio", "sui", "ugly", "ew", "huat", "daisai", "buey pai", "kanasai", "buey sai", "last warning", "chope", "steady", "OT", "macritchie", "merlion", "jialat", "kaypoh", "ORD", "tuition", "mug", "chiong", "CCA", "camp", "extra stuff", "lessons", "competitions", "go this go that", "training", "lousy", "bu ke kao", "cannot rely", "honggan", "bad", "cui", "malu", "cock", "act blur", "bo chup", "go clubbing everyday liao", "find charbor", "paktor", "eat ho liao", "makan good food", "slack", "sleep late late", "go NTUC", "wear nice nice", "put makeup", "free lobang", "pasar malam", "pasar pagi", "performance", "your laobu", "my grandfather road", "tua pek gong", "lepak one corner", "roti prata", "ah bengs", "no time", "guan yin ma", "bo bian", "liddat lor", "hum ji", "pon", "pon teng", "never set alarm", "forgot", "heck care", "go Teo Heng", "jia zhua", "sek fan", "talk cock sing song", "go steady", "gai gai", "itchy backside", "ah gua", "ah lian", "lim kopi", "yum cha", "nua", "tan ku ku", "pang sai", "tio saman", "orh beh gong", "jio mahjong kakis", "balik kampong", "boh jiak png", "jiak zhua", "Wild Card", "Wild Card", "Wild Card", "Wild Card", "Wild Card"};

    //stack 2
    String[] qns2 = {"Faster leh, we want to ____", "Wah you did so much ah? Damn ____ sia!", "Singaporeans all ____ one", "1 in 10 Singaporeans ____", "Wah I have a lot of work, my boss ____", "This weekend free anot? I thinking of ____", "Can help me dabao ____", "____ very chai leh! I sleep also dream of her", "Just now I go see doctor, he tell me i ____", "Kids nowadays anyhow mix with ____"};
    String[] ans2 = {"chope seats", "siam the saman lady", "siam the teacher", "chiong", "zao already", "see chiobu", "be number one", "double confirm", "see lengzai", "get good lobang", "spoil market", "wayang", "sibei step", "act yi ge", "hao lian", "bo liao", "steady", "hiong", "wu seh", "gey kiang", "kiasu", "kiasi", "zai", "chin cai", "sui", "yan dao", "rabak", "onz", "yaya papaya", "powderful", "kena stomp", "like to jiak liao bi", "swaku", "drama mama", "like to buey hiao bai", "sibei wu lui", "sibei tok gong", "tan jiak", "stylo milo", "kan chiong spider", "suka suka give me work", "steady pom pee pee", "siao on", "kanasai", "bodoh", "boh liao", "always like that one", "no give chance", "very the unreasonable", "sabo", "go see see look look", "tak kiu", "go sing K", "selling backside", "lepaking", "eat ho liao", "slack", "go play play", "go paktor", "playing mahjong", "siambu", "food from hawker centre", "char kway teow", "rojak", "bubble tea", "old chang kee", "barley peng", "chinchow", "ayam penyet", "mala", "chao ahlian neighbour", "ahma", "drinks stall auntie", "sibei toot classmate", "platoon sergeant", "karang guni", "orbisai", "cleaner auntie", "cher", "hairy woman", "kena flu", "too fat", "nua too much", "need exercise more", "very tam chiak", "ai stead mai", "kee siao", "cannot lim jiu", "jialat", "blur sotong", "ah boys", "malay aunties", "abang", "kakak", "BGR", "tikopek", "sam seng bo", "grab drivers", "ah peh", "ah longs", "Wild Card", "Wild Card", "Wild Card", "Wild Card", "Wild Card"};

    //stack 3
    String[] qns3 = {"Just now I saw the largest ____ in the world ", "Today someone ask me ____", "My favourite food is ____", "When I was in Tekong, I think about ____ everyday", "My boyfriends likes to ____", "What the heck you damn ____", "My laobu told me ____", "Today my cher brought ____ to class", "How do I get ____", "____ caused your house to exploded"};
    String[] ans3 = {"Bird Bird", "Melons", "Statue", "Ang Moh", "Kantang", "Kiasu Kia", "Blur Sotong", "Pangseh King", "Lengzai", "Elephant", "Ai stead mai", "if my neh neh are real", "if my face is plastic ", "where to find chiobu", "if I like Zhup Cai Png", "can tompang", "if my ang kong is permanent", "hotel di toh loh", "merlion zai na li", "simi si bojio", "Roti Prata Telok", "Nasi Lemak", "Laksa mai hum", "Sashimi with wasabi gaogao", "big black cockles", "Char Kway Teow", "Bak Chor Mee", "Rojak with extra you tiao", "Tze Char", "Murtubak", "my zharbor", "Winning Toto", "Missy in KK Hospital", "rubber toy at home", "Kopi Siew Dai", "Bookout", "being Wayang King", "Sick leave", "my laobei laobu", "Chao Geng", "go geylang paktor", "play computer games", "play with pussy cats", "play with little birds", "whistle the National Anthem", "wear fake goods", "see me with gaogao makeup", "go Hiking", "sapnu puas", "be hypebeast", "pussy eh", "guailan sia", "sian ley", "bojio sia", "like Sir Stamford Raffles eh", "freaking toot eh", "yan dao sia", "spoil market eh", "pangseh sia", "tikopek eh", "that my didi still small", "that my laopeh still strong", "that my angmoh damn lousy", "I'm her sayang", "to dabao back", "to balik kampong", "that santa claus is coming to town", "to bring my zharbo home", "to pon school", "to go pompom", "stained pads", "rotan", "halal bak kwa ", "sexist newspaper", "rotten eggs", "smelly cheebai", "singlish dictionary", "kampong ayam", "her son's 11B", "playboy magazine", "into your pants", "to Lau Pa Sat", "to Tekong", "to Sang Nila Utama House", "some milo peng", "money from ah long", "my pink IC", "to your ah gong's road", "your mama's cooking", "to kopitiam", "unused condom", "kanjiong spider", "ERP", "My pangsai", "Your wife", "Lanjiao", "Salakau (369)", "Mas Selamat", "Wild Card", "Wild Card", "Wild Card", "Wild Card", "Wild Card"};

    //stack 4
    String[] qns4 = {"Your girlfriend screamed ____", "Where can I buy ____", "She ____ in order to get into Med School", "The Cai Png auntie say ____", "My aunt say ____ during cny", "I saw the prime minister when I went to ____ yesterday", "A xiao mei mei came to me and say ____", "Every Wednesday night got ____", "____ has became the Minister of Lim Lao Bu", "The ah beng downstairs kena arrested for ____"};
    String[] ans4 = {"my laopei's name", "why you pangseh", "daddy", "ya ya papaya", "out of ecstasy", "simi daiji", "majulah singapura", "at the tikopek", "bo ta bo lampa", "alamak", "vibrating lie detector", "rubber toy", "sotong balls", "4D", "Toto", "Guay Png", "my teacher's atas pen", "your brother's bird", "yong tau foo", "your backside", "got down", "mug hard", "sell body in geylang", "move out of kampong", "sell goreng pisang in pasar malam", "become karang guni", "ta 10 shots", "tekan her clasmates", "chiong all day and night", "wayang to the chers", "walao eh you too skinny ley", "xiao mei eat more please", "wah bui ley", "jiak ba buay", "pang gang lo", "sibei sian", "order hosei liao", "wah buay tahan liao", "no money then bobian lo", "An zhua la kua simi kua", "heng ong huat ah", "ah boy guai guai ah", "ah girl lai give you angpao", "ai sing k mai", "your zharbo buay pai ley", "makan until fat like pig", "sayang ah you so big liao", "wah your chinese cmi la", "her cooking bo beh zhao", "doomzi aimai", "siamdiu", "gai gai", "pangsai", "my ahma house", "lepak one corner", "paktor", "zuo ji", "Botak Jones", "lim jiu", "chao geng", "copycat kiss the rat", "want fight ah", "don't touch my meow meow", "you goondu", "I love my kor kor", "hosei liao", "mai humji ley", "mai haolian ah", "ah lian very pretty ley", "ah beng angkong very big", "xiao mei mei doing to doomzi", "ah lian going to siamdiu", "nerds going to mug", "ah beng going to boxing classes", "xiao didi trying to slide into dms", "mothers going to atas kopitiam", "pigs flying", "an ahma walking around saying siao ah", "someone talking cock", "cleaner chilling at cbd", "mama shop auntie", "blur sotong", "Phua Chu Kang", "tikopek", "Merlion", "sampat po", "your neighbour's dog", "Chao ah gua", "Unborn baby", "lim peh", "molest", "studying", "watching auntie makan mee siam", "doing nothing", "for eating otah", "loving himself", "losing Toto", "being suay", "being a cheebai", "being chinese", "Wild Card", "Wild Card", "Wild Card", "Wild Card", "Wild Card"};
    ArrayList<String> qnList = new ArrayList<>(Arrays.asList(qns2));
    Random rand = new Random();
    int randomNum = 0;
    String drawn;

    public QuestionDeck() {
    }

    String getNextQuestion() {
        randomNum = rand.nextInt(qnList.size());
        drawn = qnList.get(randomNum);
        qnList.remove(randomNum);
        return drawn;
    }
}

class AnswerDeck {
    String[] ans1 = {"siamdiu", "tekong", "library", "kopitiam", "exercise", "istana", "tan tock seng", "bukit timah hill", "geylang", "book in", "atas", "bling bling", "chio", "sui", "ugly", "ew", "huat", "daisai", "buey pai", "kanasai", "buey sai", "last warning", "chope", "steady", "OT", "macritchie", "merlion", "jialat", "kaypoh", "ORD", "tuition", "mug", "chiong", "CCA", "camp", "extra stuff", "lessons", "competitions", "go this go that", "training", "lousy", "bu ke kao", "cannot rely", "honggan", "bad", "cui", "malu", "cock", "act blur", "bo chup", "go clubbing everyday liao", "find charbor", "paktor", "eat ho liao", "makan good food", "slack", "sleep late late", "go NTUC", "wear nice nice", "put makeup", "free lobang", "pasar malam", "pasar pagi", "performance", "your laobu", "my grandfather road", "tua pek gong", "lepak one corner", "roti prata", "ah bengs", "no time", "guan yin ma", "bo bian", "liddat lor", "hum ji", "pon", "pon teng", "never set alarm", "forgot", "heck care", "go Teo Heng", "jia zhua", "sek fan", "talk cock sing song", "go steady", "gai gai", "itchy backside", "ah gua", "ah lian", "lim kopi", "yum cha", "nua", "tan ku ku", "pang sai", "tio saman", "orh beh gong", "jio mahjong kakis", "balik kampong", "boh jiak png", "jiak zhua", "Wild Card", "Wild Card", "Wild Card", "Wild Card", "Wild Card"};
    String[] ans2 = {"chope seats", "siam the saman lady", "siam the teacher", "chiong", "zao already", "see chiobu", "be number one", "double confirm", "see lengzai", "get good lobang", "spoil market", "wayang", "sibei step", "act yi ge", "hao lian", "bo liao", "steady", "hiong", "wu seh", "gey kiang", "kiasu", "kiasi", "zai", "chin cai", "sui", "yan dao", "rabak", "onz", "yaya papaya", "powderful", "kena stomp", "like to jiak liao bi", "swaku", "drama mama", "like to buey hiao bai", "sibei wu lui", "sibei tok gong", "tan jiak", "stylo milo", "kan chiong spider", "suka suka give me work", "steady pom pee pee", "siao on", "kanasai", "bodoh", "boh liao", "always like that one", "no give chance", "very the unreasonable", "sabo", "go see see look look", "tak kiu", "go sing K", "selling backside", "lepaking", "eat ho liao", "slack", "go play play", "go paktor", "playing mahjong", "siambu", "food from hawker centre", "char kway teow", "rojak", "bubble tea", "old chang kee", "barley peng", "chinchow", "ayam penyet", "mala", "chao ahlian neighbour", "ahma", "drinks stall auntie", "sibei toot classmate", "platoon sergeant", "karang guni", "orbisai", "cleaner auntie", "cher", "hairy woman", "kena flu", "too fat", "nua too much", "need exercise more", "very tam chiak", "ai stead mai", "kee siao", "cannot lim jiu", "jialat", "blur sotong", "ah boys", "malay aunties", "abang", "kakak", "BGR", "tikopek", "sam seng bo", "grab drivers", "ah peh", "ah longs", "Wild Card", "Wild Card", "Wild Card", "Wild Card", "Wild Card"};

    ArrayList<String> ansList = new ArrayList<>(Arrays.asList(ans2));

    Random rand = new Random();
    int randomNum = 0;
    String drawn;

    public AnswerDeck() {
    }

    String getNewCard() {
        randomNum = rand.nextInt(ansList.size());
        drawn = ansList.get(randomNum);
        ansList.remove(randomNum);
        return drawn;
    }

}


class GameStatistic {
    int numberOfPlayers;
    GameParticipant[] players = new GameParticipant[8];
    int[] voteCount = {0, 0, 0, 0, 0, 0, 0, 0};
    AnswerDeck answerDeck = new AnswerDeck();
    QuestionDeck questionDeck = new QuestionDeck();

    public GameStatistic() {

    }

    QuestionDeck getQuestionDeck() {
        return questionDeck;
    }

    AnswerDeck answerDeck() {
        return answerDeck;
    }


    void setNumberOfPlayer(int number) {
        this.numberOfPlayers = number;
    }

    String getFinalAnswer(String qn, String ans) {
        String finalAnswer;
        String[] splitQn = new String[2];

        splitQn = qn.split("____");
        finalAnswer = splitQn[0] + ans + splitQn[1];
        return finalAnswer;
    }


    void getRoundWinner() {
        int count = numberOfPlayers;
        int winner = 0;
        int[] didWin = {0, 0, 0, 0, 0, 0, 0, 0};
        Boolean multipleWinners = false;
        while (count > 0) {
            voteCount[count] = 0;
            count--;
        }
        for (int i = 0; i < numberOfPlayers; i++) {
            voteCount[players[i].getVote()]++;
        }

        for (int j = 0; j < numberOfPlayers; j++) {
            if (voteCount[j] > voteCount[winner]) {
                if (multipleWinners == true) {
                    multipleWinners = false;
                    count = numberOfPlayers;
                    while (count < 0) {
                        didWin[count] = 0;
                        count--;
                    }
                } else {
                    winner = j;
                }
            } else if (voteCount[j] == voteCount[winner]) {
                multipleWinners = true;
                didWin[j] = 1;
                didWin[winner] = 1;

            }

        }

        if (multipleWinners == true) {
            count = numberOfPlayers;
            while (count > 0) {
                if (didWin[count] == 1) {
                    players[count].increasePlayerScore();
                }
                count--;
            }
        } else {
            players[winner].increasePlayerScore();
        }
    }


}

class User {
    int score;
    int playerNum;
    String playerName;

    User(String name) {
        this.playerName = name;
    }

    void setPlayerName(String name) {
        this.playerName = name;
    }

    void setPlayerNum(int num) {
        this.playerNum = num;
    }

}