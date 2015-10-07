package com.quickblox.q_municate.utils.helpers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import com.quickblox.auth.model.QBProvider;
import com.quickblox.q_municate.App;
import com.quickblox.q_municate.utils.listeners.ExistingQbSessionListener;
import com.quickblox.q_municate.utils.listeners.GlobalLoginListener;
import com.quickblox.q_municate_core.models.AppSession;
import com.quickblox.q_municate_core.models.LoginType;
import com.quickblox.q_municate_core.qb.commands.QBLoginChatCompositeCommand;
import com.quickblox.q_municate_core.qb.commands.QBLoginCompositeCommand;
import com.quickblox.q_municate_core.qb.commands.QBSocialLoginCommand;
import com.quickblox.q_municate_core.service.QBServiceConsts;
import com.quickblox.q_municate_core.utils.ConstsCore;
import com.quickblox.q_municate_db.managers.DataManager;
import com.quickblox.users.model.QBUser;

import java.util.concurrent.TimeUnit;

public class LoginHelper {

    private Context context;
    private SharedHelper appSharedHelper;
    private CommandBroadcastReceiver commandBroadcastReceiver;
    private GlobalLoginListener globalLoginListener;
    private ExistingQbSessionListener existingQbSessionListener;

    private String userEmail;
    private String userPassword;

    public LoginHelper(Context context) {
        this.context = context;
        appSharedHelper = App.getInstance().getAppSharedHelper();

        userEmail = appSharedHelper.getUserEmail();
        userPassword = appSharedHelper.getUserPassword();
    }

    public LoginHelper(Context context, ExistingQbSessionListener existingQbSessionListener) {
        this(context);
        this.existingQbSessionListener = existingQbSessionListener;
    }

    public void checkStartExistSession() {
        if (needToClearAllData()) {
            existingQbSessionListener.onStartSessionFail();
            return;
        }

        if (appSharedHelper.isSavedRememberMe()) {
            startExistSession();
        } else {
            existingQbSessionListener.onStartSessionFail();
        }
    }

    public void startExistSession() {
        boolean isEmailEntered = !TextUtils.isEmpty(userEmail);
        boolean isPasswordEntered = !TextUtils.isEmpty(userPassword);
        if ((isEmailEntered && isPasswordEntered) || (isLoggedViaFB(isPasswordEntered))) {
            runExistSession();
        } else {
            existingQbSessionListener.onStartSessionFail();
        }
    }

    public boolean isLoggedViaFB(boolean isPasswordEntered) {
        return isPasswordEntered && LoginType.FACEBOOK.equals(getCurrentLoginType());
    }

    public LoginType getCurrentLoginType() {
        return AppSession.getSession().getLoginType();
    }

    public void runExistSession() {
        //check is token valid for about 1 minute
        if (AppSession.isSessionExistOrNotExpired(TimeUnit.MINUTES.toMillis(
                ConstsCore.TOKEN_VALID_TIME_IN_MINUTES))) {
            existingQbSessionListener.onStartSessionSuccess();
        } else {
            login();
        }
    }

    public void login() {
        if (LoginType.EMAIL.equals(getCurrentLoginType())) {
            loginQB();
        } else if (LoginType.FACEBOOK.equals(getCurrentLoginType())) {
            loginFB();
        }
    }

    public void loginFB() {
        String fbToken = appSharedHelper.getFBToken();
        AppSession.getSession().closeAndClear();
        QBSocialLoginCommand.start(context, QBProvider.FACEBOOK, fbToken, null);
    }

    public void loginQB() {
        appSharedHelper.saveUsersImportInitialized(true);
        QBUser user = new QBUser(null, userPassword, userEmail);
        AppSession.getSession().closeAndClear();
        QBLoginCompositeCommand.start(context, user);
    }

    public void loginChat() {
        QBLoginChatCompositeCommand.start(context);
    }

    private boolean needToClearAllData() {
        if (DataManager.getInstance().getUserDataManager().getAll().isEmpty()) {
            App.getInstance().getAppSharedHelper().clearAll();
            AppSession.getSession().closeAndClear();
            return true;
        } else {
            return false;
        }
    }

    public void makeGeneralLogin(GlobalLoginListener globalLoginListener) {
        this.globalLoginListener = globalLoginListener;
        commandBroadcastReceiver = new CommandBroadcastReceiver();
        registerCommandBroadcastReceiver();
        login();
    }

    private void unregisterBroadcastReceiver() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(commandBroadcastReceiver);
    }

    private void registerCommandBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(QBServiceConsts.LOGIN_SUCCESS_ACTION);
        intentFilter.addAction(QBServiceConsts.LOGIN_FAIL_ACTION);

        intentFilter.addAction(QBServiceConsts.SOCIAL_LOGIN_SUCCESS_ACTION);
        intentFilter.addAction(QBServiceConsts.SOCIAL_LOGIN_FAIL_ACTION);

        intentFilter.addAction(QBServiceConsts.LOGIN_CHAT_COMPOSITE_SUCCESS_ACTION);
        intentFilter.addAction(QBServiceConsts.LOGIN_CHAT_COMPOSITE_FAIL_ACTION);

        LocalBroadcastManager.getInstance(context).registerReceiver(commandBroadcastReceiver, intentFilter);
    }

    private class CommandBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, final Intent intent) {
            if (globalLoginListener == null) {
                return;
            }

            if (intent.getAction().equals(QBServiceConsts.LOGIN_SUCCESS_ACTION)
                    || intent.getAction().equals(QBServiceConsts.SOCIAL_LOGIN_SUCCESS_ACTION)) {
                loginChat();
            } else if (intent.getAction().equals(QBServiceConsts.LOGIN_CHAT_COMPOSITE_SUCCESS_ACTION)) {
                unregisterBroadcastReceiver();
                globalLoginListener.onCompleteQbChatLogin();
            } else if (intent.getAction().equals(QBServiceConsts.LOGIN_FAIL_ACTION)
                    || intent.getAction().equals(QBServiceConsts.LOGIN_CHAT_COMPOSITE_FAIL_ACTION)
                    || intent.getAction().equals(QBServiceConsts.SOCIAL_LOGIN_FAIL_ACTION)) {
                unregisterBroadcastReceiver();
                globalLoginListener.onCompleteWithError("Login was finished with error!");
            }
        }
    }
}