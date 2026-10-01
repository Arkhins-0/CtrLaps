package com.arkhins.ctrlaps.data

import kotlinx.serialization.Serializable

/** A frequently asked question: its answer and, for a procedure, the steps in order. */
@Serializable
data class Faq(
    val id: String,
    val category: String,
    val question: String,
    val answer: String,
    val steps: List<String> = emptyList(),
    val position: Int = 0,
)

@Serializable
data class FaqsResponse(val faqs: List<Faq> = emptyList())

/** A support ticket. In a list it also carries its last message and unread count. */
@Serializable
data class Ticket(
    val id: String,
    val number: Int,
    /** "#0012" */
    val label: String,
    val category: String,
    val subject: String,
    val details: String = "",
    /** "open" or "closed" */
    val status: String,
    val createdAt: String,
    val closedAt: String? = null,
    val name: String = "",
    val email: String = "",
    val phone: String? = null,
    /** The account that raised it (developers only); null without one. */
    val userId: String? = null,
    val lastMessageAt: String? = null,
    val lastMessage: String? = null,
    val unread: Int = 0,
)

@Serializable
data class TicketsResponse(val tickets: List<Ticket> = emptyList(), val isDev: Boolean = false)

/** A ticket and its chat; to anyone but a developer, developers are "Support". */
@Serializable
data class TicketViewResponse(
    val ticket: Ticket,
    val messages: List<Message> = emptyList(),
    val isDev: Boolean = false,
    val canReply: Boolean = false,
    val canClose: Boolean = false,
    val canReopen: Boolean = false,
    val reopenUntil: String? = null,
)

@Serializable
data class RaisedTicket(val id: String? = null, val number: Int = 0, val label: String = "")

/** What a ticket can be about: the form's choices (the server checks the same list). */
val SUPPORT_CATEGORIES = listOf("Account & sign-in", "App problem", "Chats & messages", "Schedule & results", "Notifications", "Other")
