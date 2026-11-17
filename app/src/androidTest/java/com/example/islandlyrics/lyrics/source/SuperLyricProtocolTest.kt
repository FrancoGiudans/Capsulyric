@file:Suppress("DEPRECATION")

package com.example.islandlyrics.lyrics.source

import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import com.hchen.superlyricapi.ISuperLyricManager
import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.LyricListener
import com.hchen.superlyricapi.LyricView
import com.hchen.superlyricapi.Receiver
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricLine
import com.hchen.superlyricapi.SuperLyricWord
import com.hchen.superlyricapi.model.Line
import com.hchen.superlyricapi.model.Lyric
import com.hchen.superlyricapi.model.Stop
import org.junit.Assert.*
import org.junit.Test

/** Real Android Parcel/Binder tests. The legacy fixture writes only 3.4 fields. */
class SuperLyricProtocolTest {
    private val line = SuperLyricLine("hello", arrayOf(SuperLyricWord("hello", 1000, 2000)), 1000, 2000)

    @Test fun realServiceManagerLookupHandlesAnAbsentService() {
        assertNull(findSuperLyricService("capsulyric.test.superlyric.absent"))
    }

    @Test fun version34WireCallbackRetainsTextWordsAndSeparateSidecars() {
        val service = Version34Service()
        val received = mutableListOf<SuperLyricUpdate>()
        val connection = legacyConnection(service)
        try {
            assertEquals(Receiver.RegisterResult.OK, connection.open(received::add) { _, _, _, _ -> })
            service.emit34(line, SuperLyricLine("translation"), SuperLyricLine("romanization"))
            val update = received.single()
            assertEquals("legacy-line", update.mode)
            assertEquals("player", update.publisher)
            assertEquals("Song", update.title)
            assertEquals("hello", update.currentLine!!.text)
            assertEquals(1000L, update.currentLine.syllables!!.single().startTime)
            assertEquals(2000L, update.currentLine.syllables.single().endTime)
            assertEquals("translation", update.translation)
            assertEquals("romanization", update.roma)
            assertNull(update.lines)
            assertEquals(1, service.registrations)
        } finally { connection.close() }
        assertEquals(1, service.unregistrations)
    }

    @Test fun companionOnly34CallbackDoesNotInventPrimaryText() {
        val service = Version34Service()
        val received = mutableListOf<SuperLyricUpdate>()
        val connection = legacyConnection(service)
        try {
            connection.open(received::add) { _, _, _, _ -> }
            service.emit34(null, SuperLyricLine("translation"), null)
            assertNull(received.single().currentLine)
            assertEquals("translation", received.single().translation)
        } finally { connection.close() }
    }

    @Test fun anonymous34LineDoesNotInheritPreviousSongMetadata() {
        val service = Version34Service()
        val received = mutableListOf<SuperLyricUpdate>()
        val connection = legacyConnection(service)
        try {
            connection.open(received::add) { _, _, _, _ -> }
            service.emit34(line, null, null)
            service.emit34(SuperLyricLine("next song line"), null, null, title = null, artist = null)
            assertEquals("", received.last().title)
            assertEquals("", received.last().artist)
            assertEquals("next song line", received.last().currentLine!!.text)
        } finally { connection.close() }
    }

    @Test fun closedConnectionIgnoresLateBinderCallback() {
        val service = Version34Service()
        var count = 0
        val connection = legacyConnection(service)
        connection.open({ count++ }) { _, _, _, _ -> }
        val old = service.callback!!
        connection.close()
        service.emit34(line, null, null, old)
        assertEquals(0, count)
        connection.close()
        assertEquals(1, service.unregistrations)
    }

    @Test fun legacyFullDocumentSurvivesPositionOnlyFramesAndClearsOnStop() {
        val service = Version34Service()
        val received = mutableListOf<SuperLyricUpdate>()
        val connection = legacyConnection(service)
        var stopCount = 0
        try {
            connection.open(received::add) { _, _, _, _ -> stopCount++ }
            service.emitNew(SuperLyricData().setTitle("Song").setArtist("Artist").setLyricId("id")
                .setAllLyrics(arrayOf(line, SuperLyricLine("second", 3000, 4000)))
                .setCurrentLyricIndex(0))
            service.emitNew(SuperLyricData().setLyricId("id").setCurrentLyricIndex(1).setPosition(3500))
            assertEquals("legacy-full", received.last().mode)
            assertSame(received.first().lines, received.last().lines)
            assertEquals("second", received.last().currentLine!!.text)
            service.emitNew(SuperLyricData(), stopped = true)
            service.emitNew(SuperLyricData().setLyricId("id").setCurrentLyricIndex(1))
            assertEquals(1, stopCount)
            assertNull(received.last().lines)
        } finally { connection.close() }
    }

    @Test fun differentDocumentDeltaCannotReuseOldFullLyrics() {
        val service = Version34Service()
        val received = mutableListOf<SuperLyricUpdate>()
        val connection = legacyConnection(service)
        try {
            connection.open(received::add) { _, _, _, _ -> }
            service.emitNew(SuperLyricData().setLyricId("old").setAllLyrics(arrayOf(line)))
            service.emitNew(SuperLyricData().setLyricId("new").setCurrentLyricIndex(0))
            assertNull(received.last().lines)
            assertEquals("new", received.last().documentId)
        } finally { connection.close() }
    }

    @Test fun v4ReceiverCanDeliverSynchronousFirstFrameAndUnregistersOnlyOwnedListener() {
        val receiver = FakeReceiver()
        val other = object : LyricListener {
            override fun onLyric(view: LyricView) = Unit
            override fun onStop(stop: Stop) = Unit
        }
        receiver.listeners.add(other)
        receiver.first = LyricView(Lyric("id", "Song", "Artist", "", 5000,
            listOf(Line("first", 0, 1000))), 0, 0, 1f, null, "player")
        val modern = Binder()
        val received = mutableListOf<SuperLyricUpdate>()
        val connection = SuperLyricConnection({ if (it == SuperLyricConnection.V4_SERVICE) modern else null }, receiver)
        assertEquals(Receiver.RegisterResult.OK, connection.open(received::add) { _, _, _, _ -> })
        assertEquals("v4", received.single().mode)
        connection.close()
        assertEquals(listOf(other), receiver.listeners)
        assertEquals(0, receiver.unregisterAllCalls)
    }

    @Test fun modernRegistrationRejectionIsNotReportedAsSuccess() {
        val receiver = FakeReceiver().apply { result = Receiver.RegisterResult.REJECTED_QUOTA }
        val connection = SuperLyricConnection({ Binder() }, receiver)
        assertEquals(Receiver.RegisterResult.REJECTED_QUOTA, connection.open({ fail() }) { _, _, _, _ -> })
        connection.close()
        assertTrue(receiver.listeners.isEmpty())
    }

    @Test fun appearanceOfV4ServiceMakesLegacyConnectionEligibleForReconnect() {
        val service = Version34Service()
        var modern: IBinder? = null
        val connection = SuperLyricConnection({ if (it == SuperLyricConnection.V4_SERVICE) modern else service }, FakeReceiver())
        try {
            connection.open({ }) { _, _, _, _ -> }
            assertTrue(connection.isAlive())
            modern = Binder()
            assertFalse(connection.isAlive())
        } finally { connection.close() }
    }

    private fun legacyConnection(service: IBinder) = SuperLyricConnection(
        { if (it == SuperLyricConnection.LEGACY_SERVICE) service else null }, FakeReceiver()
    )

    private class FakeReceiver : Receiver {
        val listeners = mutableListOf<LyricListener>()
        var first: LyricView? = null
        var result = Receiver.RegisterResult.OK
        var unregisterAllCalls = 0
        override fun register(listener: LyricListener): Receiver.RegisterResult {
            if (result == Receiver.RegisterResult.OK) {
                listeners.add(listener)
                first?.let(listener::onLyric)
            }
            return result
        }
        override fun unregister(listener: LyricListener): Boolean { listeners.remove(listener); return listeners.isEmpty() }
        override fun unregisterAll(): Boolean { unregisterAllCalls++; listeners.clear(); return true }
        override fun isRegistered(listener: LyricListener): Boolean = listener in listeners
        override fun latest(): LyricView? = first
        override fun lastStop(): Stop? = null
    }

    private class Version34Service : Binder() {
        var callback: IBinder? = null
        var registrations = 0
        var unregistrations = 0
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(ISuperLyricManager.DESCRIPTOR); return true }
            data.enforceInterface(ISuperLyricManager.DESCRIPTOR)
            when (code) {
                FIRST_CALL_TRANSACTION + 5 -> { callback = data.readStrongBinder(); registrations++ }
                FIRST_CALL_TRANSACTION + 6 -> { data.readStrongBinder(); callback = null; unregistrations++ }
                else -> error("Unsupported 3.4 transaction $code (latest/version must not be called)")
            }
            reply?.writeNoException()
            return true
        }

        fun emit34(
            lyric: SuperLyricLine?, translation: SuperLyricLine?, secondary: SuperLyricLine?,
            target: IBinder = callback!!, title: String? = "Song", artist: String? = "Artist"
        ) = send(target, false) { parcel ->
            // Frozen 3.4 layout, verified against the 3.4 source. No 3.7 tail fields.
            parcel.writeString(title)
            parcel.writeString(artist)
            parcel.writeString("Album")
            parcel.writeParcelable(lyric, 0)
            parcel.writeParcelable(secondary, 0)
            parcel.writeParcelable(translation, 0)
            parcel.writeParcelable(null, 0) // MediaMetadata placeholder
            parcel.writeParcelable(null, 0) // PlaybackState placeholder
            parcel.writeString(null) // base64 icon
            parcel.writeBundle(Bundle().apply { putString("old-field", "value") })
        }

        fun emitNew(value: SuperLyricData, stopped: Boolean = false) =
            send(callback!!, stopped) { value.writeToParcel(it, 0) }

        private fun send(target: IBinder, stopped: Boolean, payload: (Parcel) -> Unit) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(ISuperLyricReceiver.DESCRIPTOR)
                data.writeString("player")
                data.writeInt(1)
                payload(data)
                check(target.transact(FIRST_CALL_TRANSACTION + if (stopped) 1 else 0, data, reply, 0))
                reply.readException()
            } finally { data.recycle(); reply.recycle() }
        }
    }
}
