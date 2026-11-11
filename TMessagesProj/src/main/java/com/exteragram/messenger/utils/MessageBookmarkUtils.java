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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public final class MessageBookmarkUtils {

    private static final String PREFERENCES_KEY_PREFIX = "messageBookmarks_";
    private static final int MAX_BOOKMARKS = 200;
    private static final int MAX_COLLECTION_LENGTH = 40;
    private static final int MAX_PREVIEW_LENGTH = 180;

    public interface OnMessageSelected {
        void onSelected(int messageId);
    }

    private static final class Bookmark {
        final int messageId;
        final String collection;
        final String preview;

        Bookmark(int messageId, String collection, String preview) {
            this.messageId = messageId;
            this.collection = collection;
            this.preview = preview;
        }

        String getTitle() {
            return collection + " · " + preview;
        }
    }

    private MessageBookmarkUtils() {
    }

    public static void showAddDialog(Context context, int account, long dialogId, MessageObject message, Theme.ResourcesProvider resourcesProvider) {
        ArrayList<Bookmark> bookmarks = loadBookmarks(context, account, dialogId, resourcesProvider);
        if (bookmarks == null) {
            return;
        }
        Bookmark existing = findBookmark(bookmarks, message.getId());
        if (existing != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
            builder.setTitle(LocaleController.getString("MessageBookmark", R.string.MessageBookmark));
            builder.setMessage(existing.getTitle());
            builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog, which) -> {
                bookmarks.remove(existing);
                saveBookmarks(account, dialogId, bookmarks);
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            builder.show();
            return;
        }
        if (bookmarks.size() >= MAX_BOOKMARKS) {
            showMessage(context, resourcesProvider, "MessageBookmarkLimit", R.string.MessageBookmarkLimit);
            return;
        }

        EditText collectionInput = new EditText(context);
        collectionInput.setText(LocaleController.getString("MessageBookmarkDefaultCollection", R.string.MessageBookmarkDefaultCollection));
        collectionInput.setSelectAllOnFocus(true);
        collectionInput.setSingleLine(true);
        collectionInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        collectionInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_COLLECTION_LENGTH)});

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString("MessageBookmarkCollection", R.string.MessageBookmarkCollection));
        builder.setView(collectionInput);
        builder.setPositiveButton(LocaleController.getString("Save", R.string.Save), (dialog, which) -> {
            String collection = collectionInput.getText().toString().trim();
            if (collection.isEmpty()) {
                collection = LocaleController.getString("MessageBookmarkDefaultCollection", R.string.MessageBookmarkDefaultCollection);
            }
            ArrayList<Bookmark> currentBookmarks = loadBookmarks(context, account, dialogId, resourcesProvider);
            if (currentBookmarks == null) {
                return;
            }
            if (findBookmark(currentBookmarks, message.getId()) != null) {
                return;
            }
            if (currentBookmarks.size() >= MAX_BOOKMARKS) {
                showMessage(context, resourcesProvider, "MessageBookmarkLimit", R.string.MessageBookmarkLimit);
                return;
            }
            currentBookmarks.add(new Bookmark(message.getId(), collection, getPreview(message)));
            saveBookmarks(account, dialogId, currentBookmarks);
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    public static void showBookmarks(Context context, int account, long dialogId, Theme.ResourcesProvider resourcesProvider, OnMessageSelected onMessageSelected) {
        ArrayList<Bookmark> bookmarks = loadBookmarks(context, account, dialogId, resourcesProvider);
        if (bookmarks == null) {
            return;
        }
        if (bookmarks.isEmpty()) {
            showMessage(context, resourcesProvider, "MessageBookmarksEmpty", R.string.MessageBookmarksEmpty);
            return;
        }
        LinkedHashSet<String> collections = new LinkedHashSet<>();
        for (Bookmark bookmark : bookmarks) {
            collections.add(bookmark.collection);
        }
        if (collections.size() > 1) {
            String[] collectionNames = new String[collections.size() + 1];
            collectionNames[0] = LocaleController.getString("MessageBookmarkAllCollections", R.string.MessageBookmarkAllCollections);
            int index = 1;
            for (String collection : collections) {
                collectionNames[index++] = collection;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
            builder.setTitle(LocaleController.getString("MessageBookmarks", R.string.MessageBookmarks));
            builder.setItems(collectionNames, (dialog, which) -> showBookmarkList(
                    context, account, dialogId, bookmarks, which == 0 ? null : collectionNames[which], resourcesProvider, onMessageSelected
            ));
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            builder.show();
        } else {
            showBookmarkList(context, account, dialogId, bookmarks, null, resourcesProvider, onMessageSelected);
        }
    }

    private static void showBookmarkList(Context context, int account, long dialogId, ArrayList<Bookmark> bookmarks, String collection, Theme.ResourcesProvider resourcesProvider, OnMessageSelected onMessageSelected) {
        ArrayList<Bookmark> filteredBookmarks = new ArrayList<>();
        for (Bookmark bookmark : bookmarks) {
            if (collection == null || collection.equals(bookmark.collection)) {
                filteredBookmarks.add(bookmark);
            }
        }
        CharSequence[] titles = new CharSequence[filteredBookmarks.size()];
        for (int i = 0; i < filteredBookmarks.size(); i++) {
            titles[i] = filteredBookmarks.get(i).getTitle();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(collection == null ? LocaleController.getString("MessageBookmarks", R.string.MessageBookmarks) : collection);
        builder.setItems(titles, (dialog, which) -> showBookmarkActions(context, account, dialogId, filteredBookmarks.get(which), resourcesProvider, onMessageSelected));
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private static void showBookmarkActions(Context context, int account, long dialogId, Bookmark bookmark, Theme.ResourcesProvider resourcesProvider, OnMessageSelected onMessageSelected) {
        CharSequence[] actions = {
                LocaleController.getString("Open", R.string.Open),
                LocaleController.getString("MessageBookmarkMove", R.string.MessageBookmarkMove),
                LocaleController.getString("Delete", R.string.Delete)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(bookmark.getTitle());
        builder.setItems(actions, (dialog, which) -> {
            if (which == 0) {
                onMessageSelected.onSelected(bookmark.messageId);
            } else if (which == 1) {
                showMoveBookmarkDialog(context, account, dialogId, bookmark, resourcesProvider);
            } else {
                ArrayList<Bookmark> bookmarks = loadBookmarks(context, account, dialogId, resourcesProvider);
                if (bookmarks == null) {
                    return;
                }
                Bookmark current = findBookmark(bookmarks, bookmark.messageId);
                if (current != null) {
                    bookmarks.remove(current);
                    saveBookmarks(account, dialogId, bookmarks);
                }
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private static void showMoveBookmarkDialog(Context context, int account, long dialogId, Bookmark bookmark, Theme.ResourcesProvider resourcesProvider) {
        EditText collectionInput = new EditText(context);
        collectionInput.setText(bookmark.collection);
        collectionInput.setSelectAllOnFocus(true);
        collectionInput.setSingleLine(true);
        collectionInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        collectionInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_COLLECTION_LENGTH)});

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString("MessageBookmarkCollection", R.string.MessageBookmarkCollection));
        builder.setView(collectionInput);
        builder.setPositiveButton(LocaleController.getString("Save", R.string.Save), (dialog, which) -> {
            String collection = collectionInput.getText().toString().trim();
            if (collection.isEmpty()) {
                collection = LocaleController.getString("MessageBookmarkDefaultCollection", R.string.MessageBookmarkDefaultCollection);
            }
            ArrayList<Bookmark> bookmarks = loadBookmarks(context, account, dialogId, resourcesProvider);
            if (bookmarks == null) {
                return;
            }
            for (int i = 0; i < bookmarks.size(); i++) {
                if (bookmarks.get(i).messageId == bookmark.messageId) {
                    bookmarks.set(i, new Bookmark(bookmark.messageId, collection, bookmark.preview));
                    saveBookmarks(account, dialogId, bookmarks);
                    return;
                }
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private static ArrayList<Bookmark> loadBookmarks(Context context, int account, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        String key = getKey(account, dialogId);
        String stored;
        try {
            stored = ExteraConfig.preferences.getString(key, null);
        } catch (ClassCastException e) {
            FileLog.e(e);
            showMessage(context, resourcesProvider, "MessageBookmarkStorageError", R.string.MessageBookmarkStorageError);
            return null;
        }
        ArrayList<Bookmark> bookmarks = new ArrayList<>();
        if (stored == null) {
            return bookmarks;
        }
        try {
            JSONArray array = new JSONArray(stored);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                int messageId = object.getInt("message_id");
                String collection = object.getString("collection");
                String preview = object.getString("preview");
                if (messageId != 0 && !TextUtils.isEmpty(collection)) {
                    bookmarks.add(new Bookmark(messageId, collection, preview));
                }
            }
        } catch (JSONException e) {
            FileLog.e(e);
            showMessage(context, resourcesProvider, "MessageBookmarkStorageError", R.string.MessageBookmarkStorageError);
            return null;
        }
        return bookmarks;
    }

    private static void saveBookmarks(int account, long dialogId, ArrayList<Bookmark> bookmarks) {
        JSONArray array = new JSONArray();
        try {
            for (Bookmark bookmark : bookmarks) {
                JSONObject object = new JSONObject();
                object.put("message_id", bookmark.messageId);
                object.put("collection", bookmark.collection);
                object.put("preview", bookmark.preview);
                array.put(object);
            }
        } catch (JSONException e) {
            FileLog.e(e);
            return;
        }
        ExteraConfig.preferences.edit().putString(getKey(account, dialogId), array.toString()).apply();
    }

    private static Bookmark findBookmark(ArrayList<Bookmark> bookmarks, int messageId) {
        for (Bookmark bookmark : bookmarks) {
            if (bookmark.messageId == messageId) {
                return bookmark;
            }
        }
        return null;
    }

    private static String getPreview(MessageObject message) {
        CharSequence text = message.messageText;
        String preview = text == null ? "" : text.toString().trim();
        if (preview.isEmpty()) {
            preview = LocaleController.getString("MessageBookmarkMedia", R.string.MessageBookmarkMedia);
        }
        if (preview.length() > MAX_PREVIEW_LENGTH) {
            preview = preview.substring(0, MAX_PREVIEW_LENGTH - 1) + "…";
        }
        return preview;
    }

    private static String getKey(int account, long dialogId) {
        return PREFERENCES_KEY_PREFIX + account + "_" + dialogId;
    }

    private static void showMessage(Context context, Theme.ResourcesProvider resourcesProvider, String key, int fallback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(LocaleController.getString("MessageBookmarks", R.string.MessageBookmarks));
        builder.setMessage(LocaleController.getString(key, fallback));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.show();
    }
}
