/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.utils;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

public final class ChatNotesUtils {

    private static final String PREFERENCES_KEY_PREFIX = "chatNote_";
    private static final int MAX_NOTE_LENGTH = 4000;

    private ChatNotesUtils() {
    }

    public static void showEditor(Context context, int account, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        String key = getKey(account, dialogId);
        String note;
        try {
            note = ExteraConfig.preferences.getString(key, "");
        } catch (ClassCastException e) {
            FileLog.e(e);
            AlertDialog.Builder errorBuilder = new AlertDialog.Builder(context, resourcesProvider);
            errorBuilder.setTitle(LocaleController.getString("ChatNoteTitle", R.string.ChatNoteTitle));
            errorBuilder.setMessage(LocaleController.getString("ChatNoteStorageError", R.string.ChatNoteStorageError));
            errorBuilder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
            errorBuilder.show();
            return;
        }

        EditText input = new EditText(context);
        input.setText(note);
        input.setHint(LocaleController.getString("ChatNoteHint", R.string.ChatNoteHint));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setMinLines(4);
        input.setMaxLines(10);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_NOTE_LENGTH)});

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString("ChatNoteTitle", R.string.ChatNoteTitle));
        builder.setMessage(LocaleController.getString("ChatNotePrivateInfo", R.string.ChatNotePrivateInfo));
        builder.setView(input);
        builder.setPositiveButton(LocaleController.getString("Save", R.string.Save), (dialog, which) -> {
            String updatedNote = input.getText().toString().trim();
            if (updatedNote.isEmpty()) {
                ExteraConfig.preferences.edit().remove(key).apply();
            } else {
                ExteraConfig.preferences.edit().putString(key, updatedNote).apply();
            }
        });
        if (!TextUtils.isEmpty(note)) {
            builder.setNeutralButton(LocaleController.getString("Delete", R.string.Delete), (dialog, which) ->
                    ExteraConfig.preferences.edit().remove(key).apply());
        }
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private static String getKey(int account, long dialogId) {
        return PREFERENCES_KEY_PREFIX + account + "_" + dialogId;
    }
}
