package com.sigynvs.phonejanitor.ui.common

import android.content.Context
import com.sigynvs.phonejanitor.PhoneJanitorApp
import com.sigynvs.phonejanitor.di.AppContainer

/** Reach the manual DI graph from a composable: `LocalContext.current.appContainer`. */
val Context.appContainer: AppContainer
    get() = (applicationContext as PhoneJanitorApp).container
