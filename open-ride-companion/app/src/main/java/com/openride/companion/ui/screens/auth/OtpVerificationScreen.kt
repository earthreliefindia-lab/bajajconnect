package com.openride.companion.ui.screens.auth

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openride.companion.ui.theme.ActionBlue
import com.openride.companion.ui.theme.BajajGradients
import com.openride.companion.ui.theme.CriticalRed
import com.openride.companion.ui.theme.ElevatedNavy
import com.openride.companion.ui.theme.LightBlueAccent
import com.openride.companion.ui.theme.SurfaceDarkBorder
import com.openride.companion.ui.theme.SurfaceDarkCard
import com.openride.companion.ui.theme.SurfaceDarkTextSec

@Composable
fun OtpVerificationScreen(
    viewModel: OtpViewModel,
    onVerificationSuccess: () -> Unit,
    onNavigateBackToLogin: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequesters = remember { List(4) { FocusRequester() } }

    LaunchedEffect(Unit) {
        if (focusRequesters.isNotEmpty()) {
            focusRequesters[0].requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BajajGradients.HeroNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Verification instructions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Verification Code",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "We have sent a 4-digit verification code to",
                    color = SurfaceDarkTextSec,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${uiState.dialCode} ${uiState.phoneNumber}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Edit",
                        color = LightBlueAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { onNavigateBackToLogin() }
                            .padding(4.dp)
                    )
                }
            }

            // Central: 4 Discrete OTP Input Squircle Boxes
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0..3) {
                        val digit = uiState.digits.getOrElse(i) { "" }
                        val isError = uiState.errorMessage != null
                        val borderColor = when {
                            isError -> CriticalRed
                            digit.isNotEmpty() -> ActionBlue
                            else -> SurfaceDarkBorder
                        }

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceDarkCard)
                                .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                                .clickable {
                                    focusRequesters[i].requestFocus()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicTextField(
                                value = digit,
                                onValueChange = { newText ->
                                    val nextFocus = viewModel.onDigitEntered(i, newText)
                                    if (nextFocus in 0..3 && nextFocus != i) {
                                        focusRequesters[nextFocus].requestFocus()
                                    }
                                },
                                modifier = Modifier
                                    .focusRequester(focusRequesters[i])
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.key == Key.Backspace) {
                                            val target = viewModel.onBackspace(i)
                                            if (target in 0..3) {
                                                focusRequesters[target].requestFocus()
                                            }
                                            true
                                        } else false
                                    },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                }

                // Error message
                if (uiState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.errorMessage!!,
                        color = CriticalRed,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Resend Timer Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (uiState.remainingSeconds > 0) {
                        val secFormatted = uiState.remainingSeconds.toString().padStart(2, '0')
                        Text(
                            text = "Resend code in 00:$secFormatted",
                            color = SurfaceDarkTextSec,
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "Didn't receive code? ",
                            color = SurfaceDarkTextSec,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Resend OTP",
                            color = LightBlueAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { viewModel.onResendClicked() }
                                .padding(4.dp)
                        )
                    }
                }
            }

            // Bottom: Verify & Proceed Action Button
            Button(
                onClick = {
                    viewModel.onVerifyClicked(onVerificationSuccess)
                },
                enabled = uiState.isComplete && !uiState.isLocked && !uiState.isVerifying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ActionBlue,
                    disabledContainerColor = ElevatedNavy.copy(alpha = 0.5f),
                    contentColor = Color.White,
                    disabledContentColor = SurfaceDarkTextSec
                )
            ) {
                if (uiState.isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "VERIFY & PROCEED",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
