package com.mandro.mark7.presentation.ui.user

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mandro.mark7.R
import com.mandro.mark7.domain.model.UserProfile
import com.mandro.mark7.presentation.theme.Mark7Palette
import com.mandro.mark7.presentation.theme.Mark7Theme

@Composable
fun UserSetupScreen(
    onDone: () -> Unit,
    viewModel: UserSetupViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.done.collect { onDone() }
    }

    UserSetupContent(
        ui = ui,
        onSelectUser = viewModel::selectUser,
        onContinue = viewModel::continueWithActive,
        onAddUser = viewModel::openCreateDialog,
        onCreateNameChange = viewModel::onCreateNameChange,
        onConfirmCreate = viewModel::confirmCreate,
        onDismissCreate = viewModel::dismissCreateDialog,
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
    )
}

@Composable
private fun UserSetupContent(
    ui: UserSetupUiState,
    onSelectUser: (String) -> Unit,
    onContinue: () -> Unit,
    onAddUser: () -> Unit,
    onCreateNameChange: (String) -> Unit,
    onConfirmCreate: () -> Unit,
    onDismissCreate: () -> Unit,
    onRequestDelete: (UserProfile) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Mark7Palette.Bg)
            .statusBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.user_setup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Mark7Palette.Ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        if (ui.users.isEmpty()) R.string.user_setup_sub_empty else R.string.user_setup_sub,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mark7Palette.InkMuted,
                )
                Spacer(Modifier.height(20.dp))
            }

            items(ui.users, key = { it.id }) { user ->
                UserCard(
                    user = user,
                    isActive = user.id == ui.activeUserId,
                    onClick = { onSelectUser(user.id) },
                    onDelete = { onRequestDelete(user) },
                )
            }

            item {
                AddUserCard(onClick = onAddUser)
                // 하단 고정 버튼에 가리지 않도록 여백
                Spacer(Modifier.height(if (ui.activeUser != null) 96.dp else 24.dp))
            }
        }

        // 지난 세션 활성 사용자로 바로 계속
        ui.activeUser?.let { active ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Mark7Palette.Bg)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = onContinue,
                    enabled = !ui.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mark7Palette.Accent,
                        disabledContainerColor = Mark7Palette.Line,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.user_setup_continue_as, active.name),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }

    if (ui.createDialogName != null) {
        CreateUserDialog(
            name = ui.createDialogName,
            onNameChange = onCreateNameChange,
            onConfirm = onConfirmCreate,
            onDismiss = onDismissCreate,
        )
    }

    ui.deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = onCancelDelete,
            containerColor = Mark7Palette.Surface,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = stringResource(R.string.user_delete_title, target.name),
                    fontWeight = FontWeight.Bold,
                    color = Mark7Palette.Danger,
                )
            },
            text = { Text(stringResource(R.string.user_delete_msg), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(stringResource(R.string.user_delete_confirm), fontWeight = FontWeight.Bold, color = Mark7Palette.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun UserCard(
    user: UserProfile,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Mark7Palette.Surface,
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) Mark7Palette.Accent else Mark7Palette.Line,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isActive) Mark7Palette.Accent else Mark7Palette.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isActive) Color.White else Mark7Palette.Accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Mark7Palette.Ink,
                )
                if (isActive) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.user_current_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = Mark7Palette.Accent,
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.user_delete_cd),
                    tint = Mark7Palette.InkMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AddUserCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Mark7Palette.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Mark7Palette.Accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.user_add),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Mark7Palette.Accent,
            )
        }
    }
}

@Composable
private fun CreateUserDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Mark7Palette.Surface,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.user_add), fontWeight = FontWeight.Bold, color = Mark7Palette.Ink) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.user_setup_name_hint)) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Mark7Palette.Accent,
                    unfocusedBorderColor = Mark7Palette.Line,
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onConfirm() }),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.user_setup_start), fontWeight = FontWeight.Bold, color = Mark7Palette.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun UserSetupPreviewEmpty() {
    Mark7Theme {
        UserSetupContent(
            ui = UserSetupUiState(),
            onSelectUser = {}, onContinue = {}, onAddUser = {},
            onCreateNameChange = {}, onConfirmCreate = {}, onDismissCreate = {},
            onRequestDelete = {}, onConfirmDelete = {}, onCancelDelete = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun UserSetupPreviewList() {
    Mark7Theme {
        UserSetupContent(
            ui = UserSetupUiState(
                users = listOf(
                    UserProfile("a", "민서", 1),
                    UserProfile("b", "해림", 2),
                    UserProfile("c", "코타", 3),
                ),
                activeUserId = "b",
            ),
            onSelectUser = {}, onContinue = {}, onAddUser = {},
            onCreateNameChange = {}, onConfirmCreate = {}, onDismissCreate = {},
            onRequestDelete = {}, onConfirmDelete = {}, onCancelDelete = {},
        )
    }
}
