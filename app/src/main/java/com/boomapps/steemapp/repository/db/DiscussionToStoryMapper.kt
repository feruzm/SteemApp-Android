/*
 * Copyright 2018, BoomApps LLC. 
 * All rights reserved.
*/
package com.boomapps.steemapp.repository.db

import android.text.Html
import com.boomapps.steemapp.repository.db.entities.StoryEntity
import com.boomapps.steemapp.repository.steem.DiscussionData
import com.boomapps.steemapp.repository.steem.StoryMetadata
import com.google.gson.GsonBuilder
import timber.log.Timber

class DiscussionToStoryMapper(val data: ArrayList<DiscussionData>, val accountName: String) {
    constructor(data: DiscussionData, accountName: String) : this(arrayListOf(data), accountName)

    fun map(): ArrayList<StoryEntity> {
        val outData = arrayListOf<StoryEntity>()
        for (inData in data) {
            val result = convertToStory(inData)
            if (result != null) {
                outData.add(result)
            }
        }
        return outData
    }

    val linksMarkdownPattern = Regex("\\[!\\[.*\\)]\\(.*?\\)|!\\[.*?\\)")
//    val pattern = Regex("(\\s+)|(\\\\n+)")
//    val tagsPattern = Regex("<.+?>")
    val linksPattern = Regex("((https?|ftp|gopher|telnet|file|Unsure|http):((//)|(\\\\))+[\\w\\d:#@%/;\$()~_?\\+-=\\\\\\.&]*)")

    private fun convertToStory(data: DiscussionData): StoryEntity? {
        val outValue = StoryEntity()
        val inValue = data.discussion
        if (inValue == null) {
            return inValue
        }
        outValue.entityId = inValue.id
        outValue.title = inValue.title
        outValue.author = inValue.author.name
        outValue.rawBody = inValue.body
        outValue.permlink = inValue.permlink.link
        outValue.url = "http://steemit.com${inValue.url}"
        outValue.shortText = getShortText(inValue.body)

        outValue.avatarUrl = "https://steemitimages.com/u/${outValue.author}/avatar"

        if (inValue.jsonMetadata != null) {
            val metadata = parseMetadata(inValue.jsonMetadata)
            if (metadata.imagesUrl.isNotEmpty()) {
                outValue.images = metadata.imagesUrl
                outValue.mainImageUrl = metadata.imagesUrl[0]
            }
            outValue.tags = metadata.tags
            outValue.links = metadata.links
        }
        outValue.linksNum = outValue.links.size
        outValue.votesNum = inValue.netVotes
        outValue.commentsNum = inValue.children
        outValue.price = if (inValue.pendingPayoutValue.amount > 0) {
            inValue.pendingPayoutValue.toReal().toFloat()
        } else {
            inValue.totalPayoutValue.toReal().toFloat() + inValue.curatorPayoutValue.toReal().toFloat()
        }

        var sRep = inValue.authorReputation
        val st1 = Math.log10(sRep.toDouble())
        val st2 = st1 - 9
        val st3 = st2 * 9
        val st4 = st3 + 25
        outValue.reputation = st4.toInt()
        outValue.indexInResponse = data.orderId
        Timber.d("convertToStory %s : price=[%s; %d; %d; %f]", inValue.permlink, inValue.pendingPayoutValue.symbol.name, inValue.pendingPayoutValue.amount, inValue.pendingPayoutValue.precision, inValue.pendingPayoutValue.toReal())
        outValue.created = inValue.created.dateTimeAsTimestamp
        outValue.lastUpdate = inValue.lastUpdate.dateTimeAsTimestamp
        outValue.active = inValue.active.dateTimeAsTimestamp
        Timber.d("map() >> looking for vote state for $accountName")
        if (inValue.activeVotes.isNotEmpty()) {
            val voter = inValue.activeVotes.filter {
                it.voter.name == accountName
            }
            if (voter.isNotEmpty()) {
                // get 1st
                outValue.isVoted = true
                outValue.votePervecnt = voter[0].percent
                outValue.voteDate = voter[0].time.dateTimeAsTimestamp
            }
        }
//        {
//            "voter": "grevit",
//            "weight": 0,
//            "rshares": 0,
//            "percent": 10000,
//            "reputation": 293159394,
//            "time": "2018-06-06T20:16:39"
//        },


        return outValue
    }


    private fun getShortText(input: String): String {

//        val builder = StringBuilder()
//        val lines = input.split(pattern)
//        if (lines.size > 1) {
//            for (line in lines) {
//                if (!line.startsWith("http")) {
//                    builder.append(line).append(" ")
//                }
//            }
//        }
        var formatted = Html.fromHtml(input).trim()
        formatted = formatted.replace(linksMarkdownPattern, "").trim()
        formatted = formatted.replace(linksPattern, "").trim()
        formatted = formatted.substring(0, Math.min(formatted.length, 180))
        return formatted
    }

    private fun parseMetadata(input: String): StoryMetadata {
        if (input.isEmpty()) {
            return StoryMetadata()
        }
        val gson = GsonBuilder().create()
        return gson.fromJson<StoryMetadata>(input, StoryMetadata::class.java)
    }

}