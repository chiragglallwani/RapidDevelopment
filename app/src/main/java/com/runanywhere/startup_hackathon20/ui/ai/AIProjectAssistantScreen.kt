package com.runanywhere.startup_hackathon20.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.runanywhere.startup_hackathon20.ai.AIModel
import com.runanywhere.startup_hackathon20.ai.AIProviderType
import com.runanywhere.startup_hackathon20.viewmodel.AIProjectAssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIProjectAssistantScreen(
    viewModel: AIProjectAssistantViewModel,
    onBack: () -> Unit
) {
    val currentModelId by viewModel.currentModelId.collectAsState()
    val currentProviderType by viewModel.currentProviderType.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("AI Chat", "AI Settings")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Project Assistant") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Enhanced status banner with provider info
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (currentModelId != null) 
                    MaterialTheme.colorScheme.secondaryContainer 
                else 
                    MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (currentModelId != null) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (currentProviderType != null) {
                                Text(
                                    text = "Provider: ${
                                        when (currentProviderType) {
                                            AIProviderType.RUN_ANYWHERE -> "RunAnywhere (Local)"
                                            AIProviderType.GEMINI -> "Google Gemini (Cloud)"
                                            else -> "Unknown Provider"
                                        }
                                    }",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Add reload button when there are model issues
                        if (currentModelId == null && !isLoading) {
                            IconButton(
                                onClick = { viewModel.reloadCurrentModel() }
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reload Model",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Tab content
            when (selectedTab) {
                0 -> AIChatTab(viewModel, isLoading)
                1 -> {
                    // Use the new AISettingsScreen for better provider management
                    AISettingsScreen(
                        onNavigateBack = { /* Already in settings tab */ },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun AIChatTab(viewModel: AIProjectAssistantViewModel, isLoading: Boolean) {
    var userInput by remember { mutableStateOf("") }
    val executionResult by viewModel.executionResult.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "AI Project Assistant",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Give me natural language commands to manage your projects and tasks.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // Example commands card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Example Commands:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                val examples = listOf(
                    "Create a mobile app project with UI design tasks",
                    "Update the website project description",
                    "Delete the old test project",
                    "Assign the API task to john@company.com",
                    "Create tasks for user authentication and database setup"
                )

                examples.forEach { example ->
                    Text(
                        "• $example",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = userInput,
            onValueChange = { userInput = it },
            label = { Text("Enter your command") },
            placeholder = { Text("Create a project for...") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            minLines = 2,
            maxLines = 4
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                if (userInput.isNotEmpty()) {
                    viewModel.processNaturalLanguageCommand(userInput)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && userInput.isNotEmpty()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isLoading) "Processing..." else "Execute Command")
        }

        // Show execution result
        executionResult?.let { result ->
            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.success)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (result.success) "✅ Command Executed" else "❌ Command Failed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.clearExecutionResult() }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        result.message,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (result.actions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Actions: ${result.actions.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (result.createdProjectId != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Created Project ID: ${result.createdProjectId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (result.createdTaskIds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Created ${result.createdTaskIds.size} task(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Clear input button
        if (userInput.isNotEmpty()) {
            TextButton(
                onClick = {
                    userInput = ""
                    viewModel.clearExecutionResult()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Input")
            }
        }
    }
}

// Legacy AISettingsTab removed - using the new AISettingsScreen instead
