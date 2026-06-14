package com.amazon.campusflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.amazon.campusflow.ui.theme.CampusFlowTheme

import com.amazon.campusflow.ui.chat.ChatScreen
import com.amazon.campusflow.data.AppDatabase
import com.amazon.campusflow.ui.chat.ChatViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import androidx.compose.runtime.*
import com.amazon.campusflow.ui.dashboard.DashboardScreen
import com.amazon.campusflow.ui.dashboard.DashboardViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        
        val database = AppDatabase.getDatabase(this)
        val dao = database.chatMessageDao()
        val awsService = com.amazon.campusflow.data.AwsService()
        val chatFactory = ChatViewModelFactory(dao, awsService)
        val dashboardFactory = DashboardViewModelFactory(awsService)

        enableEdgeToEdge()
        setContent {
            var currentScreen by remember { mutableStateOf("dashboard") }
            val dashboardViewModel: com.amazon.campusflow.ui.dashboard.DashboardViewModel = viewModel(factory = dashboardFactory)
            val chatViewModel: com.amazon.campusflow.ui.chat.ChatViewModel = viewModel(factory = chatFactory)

            CampusFlowTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (currentScreen == "dashboard") {
                        DashboardScreen(
                            modifier = Modifier.padding(innerPadding),
                            viewModel = dashboardViewModel,
                            onNavigateToChat = { currentScreen = "chat" }
                        )
                    } else if (currentScreen == "chat") {
                        ChatScreen(
                            modifier = Modifier.padding(innerPadding),
                            viewModel = chatViewModel,
                            onNavigateBack = { 
                                dashboardViewModel.refreshEvents()
                                currentScreen = "dashboard" 
                            }
                        )
                    }
                }
            }
        }
    }
}