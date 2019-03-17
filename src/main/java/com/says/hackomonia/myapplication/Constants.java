package com.says.hackomonia.myapplication;

public class Constants {
    // Request codes for the UIs that we show with startActivityForResult:
    public static final int RC_SELECT_PLAYERS = 10000;
    public static final int RC_INVITATION_INBOX = 10001;
    public static final int RC_WAITING_ROOM = 10002;
    // Request code used to invoke sign in user interactions.
    public static final int RC_SIGN_IN = 9001;

    public static final char MESSAGE_PLAYERNAME = 'N';
    public static final char MESSAGE_RESTARTGAME = 'O';
    public static final char MESSAGE_READY_GETREADYDIALOGSHOWN = 'R';
    public static final char MESSAGE_CATEGORYSELECTION = 'C';
    public static final char MESSAGE_SPELLUSED = 'S';
    public static final char MESSAGE_UPDATESCORE = 'U';
    public static final char MESSAGE_FINISHRECORDING = 'f';
    public static final char MESSAGE_FINISHVOTING = 'v';

    public static final int IS_READY = 1;
    public static final int RESTART_YES = 0;
    public static final int RESTART_NO = 1;

    public static boolean signedIn = false; // used to determine if user has sign in
}
