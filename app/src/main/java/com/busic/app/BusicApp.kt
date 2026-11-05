package com.busic.app

import android.app.Application
import com.busic.app.data.BiliApi
import com.busic.app.data.LocalStore
import com.busic.app.player.PlayerRepository

class BusicApp : Application() {
    lateinit var api: BiliApi
        private set
    lateinit var local: LocalStore
        private set
    lateinit var player: PlayerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        api = BiliApi(this)
        local = LocalStore(this)
        player = PlayerRepository(this, api, local)
        instance = this
    }

    companion object {
        private lateinit var instance: BusicApp

        fun get(): BusicApp = instance
    }
}
