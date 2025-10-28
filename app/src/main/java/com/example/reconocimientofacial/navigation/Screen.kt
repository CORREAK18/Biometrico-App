package com.example.reconocimientofacial.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Register : Screen("register")
    object Recognize : Screen("recognize")
    object ViewAll : Screen("view_all")
}
