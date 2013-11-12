package com.brentandjody.stenokeyboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

public class StenoKeyboard extends InputMethodService {

    private static final int MAX_CANDIDATES = 20;

    private Dictionary dictionary;
    private TouchLayer keyboardView;
    private LinearLayout candidatesView;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    @Override
    public void onCreate() {
        super.onCreate();
        dictionary = new Dictionary(this, false);
        dictionary.setOnDictionaryResetListener(new Dictionary.OnDictionaryResetListener() {
            @Override
            public void onDictionaryReset() {
                if (keyboardView != null) {
                    keyboardView.lock();
                }
            }
        });
        dictionary.setOnDictionaryLoadedListener(new Dictionary.OnDictionaryLoadedListener() {
            @Override
            public void onDictionaryLoaded() {
                if (keyboardView != null) {
                    keyboardView.unlock();
                }
            }
        });
        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals("pref_key_dictionary_count") || key.equals("pref_key_use_default_dictionary")) {
                    if (BuildConfig.DEBUG) Log.d("StenoKeyboard", "Setting Changed");
                    loadDictionaries();
                }
            }
        };
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        loadDictionaries();
    }

    @Override
    public View onCreateInputView() {
        super.onCreateInputView();
        LayoutInflater layoutInflater = getLayoutInflater();
        keyboardView = (TouchLayer) layoutInflater.inflate(R.layout.keyboard, null);
        keyboardView.setOnStrokeCompleteListener(new TouchLayer.OnStrokeCompleteListener() {
            @Override
            public void onStrokeComplete() {
                if (dictionary.isLoaded()) {
                    String stroke = keyboardView.getStroke();
                    String message = dictionary.translate(stroke);
                    populateCandidates(dictionary.getCandidates());
                    if (BuildConfig.DEBUG) Log.d("onCreateInputView", "sent stroke:"+stroke+"   translation:"+message);
                    sendText(message);
                } else {
                    Toast.makeText(StenoKeyboard.this, "Dictionary not yet loaded", Toast.LENGTH_SHORT).show();
                }
            }
        });
        Button settingsButton = (Button) keyboardView.findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });
        if (! dictionary.isLoaded()) {
            keyboardView.lock();
        }
        return keyboardView;
    }

    @Override
    public void onUnbindInput() {
        super.onUnbindInput();
        sendText(dictionary.flush());
        dictionary.purge();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        sendText(dictionary.flush());
        dictionary.purge();
    }

    @Override
    public View onCreateCandidatesView() {
        super.onCreateCandidatesView();
        LayoutInflater layoutInflater = getLayoutInflater();
        HorizontalScrollView scrollView = (HorizontalScrollView) layoutInflater.inflate(R.layout.candidates, null);
        candidatesView = (LinearLayout) scrollView.findViewById(R.id.candidatesView);
        return scrollView;
    }

    private void populateCandidates(List<Definition> candidates) {
        candidatesView.removeAllViews();
        LayoutInflater layoutInflater = getLayoutInflater();
        int count = 0;
        for (Definition candidate : candidates) {
            if (count < MAX_CANDIDATES) {
                TextView tv = (TextView) layoutInflater.inflate(R.layout.candidate, null);
                tv.setText(candidate.getTranslation());
                tv.setHint(candidate.getStroke());
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String phrase = ((TextView) view).getText().toString();
                        String stroke = ((TextView) view).getHint().toString();
                        // build the a temporary queue for history
                        String strokesInQueue = dictionary.strokesInQueue();
                        Deque<String> strokeQ = new LinkedBlockingDeque<String>();
                        for (String s : strokesInQueue.split("/")) {
                            strokeQ.add(s);
                        }
                        // do not repeat strokes already in queue
                        strokeQ.add(stroke.replace(strokesInQueue+"/",""));
                        dictionary.updateHistory(strokeQ, phrase);
                        dictionary.clearQ();
                        sendText(phrase);
                        candidatesView.removeAllViews();
                        setCandidatesViewShown(false);
                    }
                });
                tv.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        Toast.makeText(StenoKeyboard.this, ((TextView) view).getHint(), Toast.LENGTH_LONG).show();
                        return false;
                    }
                });
                candidatesView.addView(tv);
            }
            count ++;
        }
        setCandidatesViewShown(candidatesView.getChildCount() > 0);
    }

    private void sendText(String message) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return; //short circuit
        // deals with backspaces
        if (message.contains("\b")) {
            message = handleBackspaces(connection, message);
        }
        connection.commitText(message, 1);
    }

    private String handleBackspaces(InputConnection connection, String message) {
        int i = 0;
        while (i < message.length() && message.charAt(i)=='\b')
            i++;
        // delete the space at the end
        if (i > 0) {
            connection.deleteSurroundingText(1,0);
            message = message.substring(1);
            i--;
        }
        // if there are still backspaces, delete a whole word
        if (i > 0) {
            deleteWord(connection, i);
            message = message.substring(i);
        }
        return message;
    }

    private void deleteWord(InputConnection connection, int size) {
        // ensure the word left of the cursor is the correct size, and delete it.
        // if not, delete back to the prior space and purge the history and strokeQ
        // leave a space a the end
        String word = connection.getTextBeforeCursor(size+1, 0).toString();
        if (word.length() == size || (word.length() > 0 && word.charAt(0) == ' ')) {  // if first word, or...
            connection.deleteSurroundingText(size, 0);
        } else {
            // delete a single character.  If it was a space, and size > 1, delete the next word
            String c = connection.getTextBeforeCursor(1,0).toString();
            if (c.length() == 1) {
                connection.deleteSurroundingText(1,0);
                if (c.equals(" ")) {
                    c = connection.getTextBeforeCursor(1,0).toString();
                    while (c.length() == 1 && !c.equals(" ")) {
                        connection.deleteSurroundingText(1,0);
                        c = connection.getTextBeforeCursor(1,0).toString();
                    }
                }
            }
            dictionary.purge();
        }
        if (!connection.getTextBeforeCursor(1,0).toString().equals(" ") && size > 1) {
            connection.commitText(" ", 1);
        }
    }

    private void loadDictionaries() {
        List<String> dictionaries = new ArrayList<String>();
        String dict_name;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // default dictionary?
        if (prefs.getBoolean("pref_key_use_default_dictionary", true)) {
            dict_name = Dictionary.getDictFile();
            if (BuildConfig.DEBUG) Log.d("loadDictionaries", "loading: " + dict_name);
            dictionaries.add(dict_name);
        }
        // personal dictionaries
        int dict_count = prefs.getInt("pref_key_dictionary_count", 0);
        for (int i=0; i<dict_count; i++) {
            dict_name = prefs.getString("pref_key_personal_dictionary_"+(i+1), "");
            if (BuildConfig.DEBUG) Log.d("loadDictionaries", "loading: " + dict_name);
            if (!dict_name.isEmpty()) {
                dictionaries.add(dict_name);
            }
        }
        if (! dictionaries.isEmpty()) {
            // lock keyboard
            if (keyboardView != null) keyboardView.lock();
            // replace dictionaries
            dictionary.unload();
            dictionary.loadDictionaries(dictionaries.toArray(new String[0]));
        }
    }

}
