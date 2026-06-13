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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(this)
        val dao = database.chatMessageDao()
        val factory = ChatViewModelFactory(dao)

        enableEdgeToEdge()
        setContent {
            CampusFlowTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChatScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel(factory = factory)
                    )
                }
            }
        }
    }
}