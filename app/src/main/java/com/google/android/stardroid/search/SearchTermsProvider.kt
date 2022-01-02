// Copyright 2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.search

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import com.google.android.stardroid.StardroidApplication
import com.google.android.stardroid.layers.LayerManager
import com.google.android.stardroid.util.MiscUtil.getTag
import javax.inject.Inject

/**
 * Provides search suggestions for a list of words and their definitions.
 */
class SearchTermsProvider : ContentProvider() {
  data class SearchTerm(var query: String, var origin: String)

  @Inject
  lateinit var layerManager: LayerManager

  override fun onCreate(): Boolean {
    maybeInjectMe()
    return true
  }

  private var alreadyInjected = false
  private fun maybeInjectMe(): Boolean {
    // Ugh.  Android's separation of content providers from their owning apps makes this
    // almost impossible.  TODO(jontayler): revisit and see if we can make this less
    // nasty.
    if (alreadyInjected) {
      return true
    }
    val appContext = context?.applicationContext as? StardroidApplication ?: return false
    val component = appContext.applicationComponent
    component.inject(this)
    alreadyInjected = true
    return true
  }

  override fun query(
    uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?,
    sortOrder: String?
  ): Cursor? {
    Log.d(TAG, "Got query for $uri")
    if (!maybeInjectMe()) {
      return null
    }
    require(TextUtils.isEmpty(selection)) { "selection not allowed for $uri" }
    require(!(selectionArgs != null && selectionArgs.size != 0)) { "selectionArgs not allowed for $uri" }
    require(TextUtils.isEmpty(sortOrder)) { "sortOrder not allowed for $uri" }
    if (uriMatcher.match(uri) == SEARCH_SUGGEST) {
      var query: String? = null
      if (uri.pathSegments.size > 1) {
        query = uri.lastPathSegment
      }
      Log.d(TAG, "Got suggestions query for $query")
      return getSuggestions(query)
    }
    throw IllegalArgumentException("Unknown URL $uri")
  }

  private fun getSuggestions(query: String?): Cursor {
    val cursor = MatrixCursor(COLUMNS)
    if (query == null) {
      return cursor
    }
    val results = layerManager.getObjectNamesMatchingPrefix(query)
    Log.d("SearchTermsProvider", "Got results n=" + results.size)
    for (result in results) {
      cursor.addRow(columnValuesOfSuggestion(result))
    }
    return cursor
  }

  private fun columnValuesOfSuggestion(suggestion: SearchTerm): Array<String> {
    return arrayOf<String>(
      Integer.toString(id++),  // _id
      suggestion.query,  // query
      suggestion.query,  // text1
      suggestion.origin
    )
  }

  /**
   * All queries for this provider are for the search suggestion mime type.
   */
  override fun getType(uri: Uri): String {
    if (uriMatcher.match(uri) == SEARCH_SUGGEST) {
      return SearchManager.SUGGEST_MIME_TYPE
    }
    throw IllegalArgumentException("Unknown URL $uri")
  }

  override fun insert(uri: Uri, values: ContentValues?): Uri? {
    throw UnsupportedOperationException()
  }

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
    throw UnsupportedOperationException()
  }

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<String>?
  ): Int {
    throw UnsupportedOperationException()
  }

  companion object {
    private val TAG = getTag(SearchTermsProvider::class.java)
    var AUTHORITY = "com.google.android.stardroid.searchterms"
    val CONTENT_URI = Uri.parse("content://" + AUTHORITY)
    private const val SEARCH_SUGGEST = 0
    private val uriMatcher = buildUriMatcher()

    /**
     * The columns we'll include in our search suggestions.
     */
    private val COLUMNS = arrayOf(
      "_id",  // must include this column
      SearchManager.SUGGEST_COLUMN_QUERY,
      SearchManager.SUGGEST_COLUMN_TEXT_1,
      SearchManager.SUGGEST_COLUMN_TEXT_2
    )

    /**
     * Sets up a uri matcher for search suggestion and shortcut refresh queries.
     */
    private fun buildUriMatcher(): UriMatcher {
      val matcher = UriMatcher(UriMatcher.NO_MATCH)
      matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGEST)
      matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH_SUGGEST)
      return matcher
    }

    private var id = 0
  }
}