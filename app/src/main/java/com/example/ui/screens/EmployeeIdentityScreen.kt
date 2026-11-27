package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.BranchOptions
import com.example.data.model.EmployeeIdentity
import com.example.data.model.EmployeeIdentityValidation
import com.example.ui.components.MueblesAmericaLogo
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandRedSoft
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeIdentityScreen(
    existingIdentity: EmployeeIdentity?,
    userEmail: String?,
    defaultNameHint: String?,
    isBusy: Boolean,
    onRegisterIdentity: (
        fullName: String,
        payrollNumber: String,
        branchName: String,
        onError: (String) -> Unit
    ) -> Unit,
    onAssignReleasedBranch: (
        branchName: String,
        onError: (String) -> Unit
    ) -> Unit,
    onSignOut: () -> Unit
) {
    val isBranchOnlyReassignment = existingIdentity?.requiresBranchOnlyReassignment == true

    var fullName by rememberSaveable(existingIdentity?.id, isBranchOnlyReassignment) {
        mutableStateOf(
            if (isBranchOnlyReassignment) existingIdentity?.fullName.orEmpty()
            else defaultNameHint.orEmpty().takeIf { it != "Usuario MA" }.orEmpty()
        )
    }
    var payrollDigits by rememberSaveable(existingIdentity?.id, isBranchOnlyReassignment) {
        mutableStateOf(
            if (isBranchOnlyReassignment) {
                EmployeeIdentityValidation.extractPayrollDigits(existingIdentity?.payrollNumber.orEmpty())
            } else {
                ""
            }
        )
    }
    var selectedBranch by rememberSaveable(existingIdentity?.id, isBranchOnlyReassignment) {
        mutableStateOf("")
    }
    var branchMenuExpanded by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(existingIdentity?.id, isBranchOnlyReassignment) {
        if (isBranchOnlyReassignment && existingIdentity != null) {
            fullName = existingIdentity.fullName
            payrollDigits = EmployeeIdentityValidation.extractPayrollDigits(existingIdentity.payrollNumber)
            selectedBranch = ""
            formError = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFAFBFD), Color(0xFFF5F8FD))
                )
            )
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MueblesAmericaLogo(
                globeSize = 40.dp,
                fontSize = 23.sp,
                showTrademark = true
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.brand_subtitle),
                style = MaterialTheme.typography.labelLarge,
                color = BrandNavy
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("employee_identity_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = BrandNavy.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Badge,
                                    contentDescription = null,
                                    tint = BrandNavy
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ficha de Identificación",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = userEmail ?: "Empleado",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isBranchOnlyReassignment) {
                        Surface(
                            color = WarningAmberSoft,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("branch_reassignment_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "El administrador liberó tu sucursal. Tu Nombre y Nómina permanecen bloqueados; selecciona tu nueva sucursal asignada para continuar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WarningAmber
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Completa tu Ficha de Identificación para activar tu sesión. Una vez guardada, tus datos quedarán vinculados permanentemente a tu cuenta y solo el administrador podrá modificarlos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            if (!isBranchOnlyReassignment) {
                                fullName = it.take(80)
                                formError = null
                            }
                        },
                        enabled = !isBranchOnlyReassignment && !isBusy,
                        readOnly = isBranchOnlyReassignment,
                        label = { Text("Nombre completo del empleado") },
                        placeholder = { Text("Ej. Juan Pérez López") },
                        leadingIcon = {
                            Icon(Icons.Filled.Person, contentDescription = null)
                        },
                        trailingIcon = if (isBranchOnlyReassignment) {
                            { Icon(Icons.Filled.Lock, contentDescription = "Bloqueado", modifier = Modifier.size(18.dp)) }
                        } else null,
                        supportingText = if (isBranchOnlyReassignment) {
                            { Text("Bloqueado · Vinculado a tu ficha") }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("identity_fullname_input")
                    )

                    val hasCompletePayroll =
                        payrollDigits.length == EmployeeIdentityValidation.PAYROLL_DIGITS_LENGTH
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            color = if (isBranchOnlyReassignment) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            } else {
                                BrandNavy.copy(alpha = 0.08f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isBranchOnlyReassignment) {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                } else {
                                    BrandNavy.copy(alpha = 0.45f)
                                }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .width(56.dp)
                                .height(56.dp)
                                .testTag("identity_payroll_prefix")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = EmployeeIdentityValidation.PAYROLL_PREFIX,
                                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isBranchOnlyReassignment) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        BrandNavy
                                    }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = payrollDigits,
                            onValueChange = { raw ->
                                if (!isBranchOnlyReassignment) {
                                    payrollDigits = EmployeeIdentityValidation.filterPayrollDigits(raw)
                                    formError = null
                                }
                            },
                            enabled = !isBranchOnlyReassignment && !isBusy,
                            readOnly = isBranchOnlyReassignment,
                            label = { Text("Nómina (6 dígitos)") },
                            placeholder = { Text("019753") },
                            leadingIcon = {
                                Icon(Icons.Filled.Tag, contentDescription = null)
                            },
                            trailingIcon = if (isBranchOnlyReassignment) {
                                { Icon(Icons.Filled.Lock, contentDescription = "Bloqueado", modifier = Modifier.size(18.dp)) }
                            } else null,
                            supportingText = {
                                val preview = EmployeeIdentityValidation.formatFullPayrollFromDigits(payrollDigits)
                                Text(
                                    text = when {
                                        isBranchOnlyReassignment ->
                                            "Bloqueado · Nómina única asignada (${existingIdentity?.displayPayroll.orEmpty()})"
                                        hasCompletePayroll ->
                                            "Nómina completa: $preview (6/6 dígitos)"
                                        payrollDigits.isNotEmpty() ->
                                            "Captura exactamente 6 dígitos (${payrollDigits.length}/6) · Ej. E019753"
                                        else ->
                                            "Escribe los 6 dígitos después de la E (${payrollDigits.length}/6) · Ej. E019753"
                                    },
                                    color = when {
                                        isBranchOnlyReassignment -> MaterialTheme.colorScheme.onSurfaceVariant
                                        hasCompletePayroll -> SuccessGreen
                                        payrollDigits.isNotEmpty() -> BrandRed
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            },
                            isError = !isBranchOnlyReassignment && payrollDigits.isNotEmpty() && !hasCompletePayroll,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("identity_payroll_input")
                        )
                    }

                    ExposedDropdownMenuBox(
                        expanded = branchMenuExpanded,
                        onExpandedChange = {
                            if (!isBusy) branchMenuExpanded = !branchMenuExpanded
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedBranch.ifBlank { "Elige tu sucursal asignada..." },
                            onValueChange = {},
                            readOnly = true,
                            enabled = !isBusy,
                            label = { Text("Sucursal asignada") },
                            leadingIcon = {
                                Icon(Icons.Filled.Storefront, contentDescription = null, tint = BrandNavy)
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchMenuExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("identity_branch_dropdown"),
                            shape = RoundedCornerShape(14.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = branchMenuExpanded,
                            onDismissRequest = { branchMenuExpanded = false }
                        ) {
                            BranchOptions.ALL_BRANCHES.forEach { branch ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = branch,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Storefront,
                                            contentDescription = null,
                                            tint = BrandNavy
                                        )
                                    },
                                    onClick = {
                                        selectedBranch = branch
                                        branchMenuExpanded = false
                                        formError = null
                                    },
                                    modifier = Modifier.testTag("identity_branch_option_${branch.replace(" ", "_").lowercase()}")
                                )
                            }
                        }
                    }

                    if (formError != null) {
                        Surface(
                            color = BrandRedSoft,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("identity_error_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = BrandRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = formError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrandRed
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Button(
                        onClick = {
                            if (isBranchOnlyReassignment) {
                                if (selectedBranch !in BranchOptions.ALL_BRANCHES) {
                                    formError = "Selecciona tu nueva sucursal asignada."
                                } else {
                                    formError = null
                                    onAssignReleasedBranch(selectedBranch) { err ->
                                        formError = err
                                    }
                                }
                            } else {
                                val fullPayroll = EmployeeIdentityValidation.formatFullPayrollFromDigits(payrollDigits)
                                val validationMsg = EmployeeIdentityValidation.validateFullRegistration(
                                    fullName = fullName,
                                    payrollNumberRaw = fullPayroll,
                                    branchName = selectedBranch
                                )
                                if (validationMsg != null) {
                                    formError = validationMsg
                                } else {
                                    formError = null
                                    onRegisterIdentity(fullName, fullPayroll, selectedBranch) { err ->
                                        formError = err
                                    }
                                }
                            }
                        },
                        enabled = !isBusy && (isBranchOnlyReassignment || hasCompletePayroll),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("submit_employee_identity_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandNavy,
                            contentColor = Color.White
                        )
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isBranchOnlyReassignment) {
                                    "Confirmar Nueva Sucursal"
                                } else {
                                    "Guardar Ficha y Continuar"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = onSignOut, enabled = !isBusy) {
                            Text("Cambiar cuenta de Google")
                        }
                    }
                }
            }
        }
    }
}
