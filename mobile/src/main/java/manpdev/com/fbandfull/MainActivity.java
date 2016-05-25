package manpdev.com.fbandfull;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.TwitterAuthProvider;
import com.twitter.sdk.android.Twitter;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterAuthToken;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;

import manpdev.com.fbandfull.databinding.MainActivityLayoutBinding;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "MainActivity";
    private static final int GOOGLE_SIGN_IN = 345;

    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private MainActivityLayoutBinding mViewBinding;

    private GoogleApiClient mGoogleApiClient;
    private CallbackManager mFBCallbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mViewBinding = DataBindingUtil.setContentView(this, R.layout.main_activity_layout);

        initFirebaseAuth();

        initGoogleSignIn();

        initFacebookSignIn();

        initTwitterSignIn();

        mViewBinding.btSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!TextUtils.isEmpty(mViewBinding.tvUsername.getText())
                        && !TextUtils.isEmpty(mViewBinding.tvPassword.getText()))
                    getCredentialsFromEmailAndPassword();
            }
        });

        mViewBinding.btSignUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!TextUtils.isEmpty(mViewBinding.tvUsername.getText())
                        && !TextUtils.isEmpty(mViewBinding.tvPassword.getText()))
                    createFirebaseUserWithCredentials();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        this.mAuth.addAuthStateListener(this.mAuthListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (this.mAuthListener != null)
            mAuth.removeAuthStateListener(this.mAuthListener);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mFBCallbackManager != null)
            mFBCallbackManager.onActivityResult(requestCode, resultCode, data);

        mViewBinding.bnTwitterSignIn.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GOOGLE_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                GoogleSignInAccount account = result.getSignInAccount();
                getCredentialsFromGoogle(account);
            }
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    private void initFirebaseAuth() {
        this.mAuth = FirebaseAuth.getInstance();

        this.mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
                    launchUser();
                }
            }
        };
    }

    private void initFacebookSignIn() {
        LoginManager.getInstance().logOut();
        mFBCallbackManager = CallbackManager.Factory.create();
        mViewBinding.bnFacebookSignIn.setReadPermissions("email", "public_profile");

        mViewBinding.bnFacebookSignIn.registerCallback(mFBCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                Log.d(TAG, "facebook:onSuccess:" + loginResult);
                getCredentialsFromFacebok(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "onCancel: ");
            }

            @Override
            public void onError(FacebookException error) {
                Log.e(TAG, "onError: ", error);
            }
        });
    }

    private void initGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(getString(R.string.auth_user_id))
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        mViewBinding.bnGoogleSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                googleSignIn();
            }
        });
    }

    private void initTwitterSignIn() {
        Twitter.getSessionManager().clearActiveSession();
        Twitter.logOut();

        mViewBinding.bnTwitterSignIn.setCallback(new Callback<TwitterSession>() {
            @Override
            public void success(Result<TwitterSession> result) {
                getCredentialsFromTwitter(result.data.getAuthToken());
            }

            @Override
            public void failure(TwitterException exception) {
                Log.d("TwitterKit", "Login with Twitter failure", exception);
            }
        });
    }

    private void googleSignIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, GOOGLE_SIGN_IN);
    }

    private void getCredentialsFromEmailAndPassword() {
        Log.d(TAG, "getCredentialsFromEmailAndPassword() called");

        AuthCredential credential = EmailAuthProvider.getCredential(mViewBinding.tvUsername.getText().toString(),
                mViewBinding.tvPassword.getText().toString());

        firebaseAuthWithCredentials(credential);
    }

    private void getCredentialsFromGoogle(GoogleSignInAccount acct) {
        Log.d(TAG, "firebaseAuthWithGooogle:" + acct.getId() + "/" + acct.getDisplayName());
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        firebaseAuthWithCredentials(credential);
    }

    private void getCredentialsFromFacebok(AccessToken token) {
        Log.d(TAG, "getCredentialsFromFacebok:" + token);
        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        firebaseAuthWithCredentials(credential);
    }

    private void getCredentialsFromTwitter(TwitterAuthToken token) {
        Log.d(TAG, "getCredentialsFromTwitter");
        AuthCredential credential = TwitterAuthProvider.getCredential(token.token, token.secret);
        firebaseAuthWithCredentials(credential);
    }

    private void firebaseAuthWithCredentials(AuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "signInWithCredential:onComplete:" + task.isSuccessful());
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "signInWithCredential", task.getException());
                            Toast.makeText(MainActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                            mViewBinding.tvPassword.setText("");
                        }
                    }
                });
    }

    private void createFirebaseUserWithCredentials() {
        mAuth.createUserWithEmailAndPassword(mViewBinding.tvUsername.getText().toString(), mViewBinding.tvPassword.getText().toString())
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "signInWithCredential:onComplete:" + task.isSuccessful());
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "signInWithCredential", task.getException());
                            Toast.makeText(MainActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void launchUser() {
        Intent i = new Intent(MainActivity.this, UserActivity.class);
        startActivity(i);
        finish();
    }
}
