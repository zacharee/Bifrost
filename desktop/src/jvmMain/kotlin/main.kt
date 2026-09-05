@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "EXPOSED_PARAMETER_TYPE")

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.bugsnag.Severity
import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLaf
import com.mayakapps.compose.windowstyler.WindowBackdrop
import com.mayakapps.compose.windowstyler.WindowFrameStyle
import com.mayakapps.compose.windowstyler.WindowStyle
import com.sun.jna.ptr.IntByReference
import dev.zwander.compose.alertdialog.LocalWindowDecorations
import dev.zwander.compose.rememberThemeInfo
import dev.zwander.kmp.platform.HostArch
import dev.zwander.kmp.platform.HostOS
import dev.zwander.kmp.platform.OSVersion
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.skia.DirectContext
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.RenderException
import org.jetbrains.skiko.SkiaLayer
import tk.zwander.common.GradleConfig
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.generated.resources.icon_rounded
import tk.zwander.common.util.BifrostSettings
import tk.zwander.common.util.BugsnagUtils
import tk.zwander.common.util.isWindows11
import tk.zwander.common.util.jna.windows.DwmImpl
import tk.zwander.common.util.jna.windows.DwmWindowAttribute
import tk.zwander.common.util.jna.windows.hwnd
import tk.zwander.common.util.jna.windows.toBgr
import tk.zwander.commonCompose.MainView
import tk.zwander.commonCompose.util.toAwtColor
import tk.zwander.commonCompose.view.LocalMenuBarHeight
import tk.zwander.commonCompose.view.LocalPagerState
import tk.zwander.commonCompose.view.MacMenuBar
import tk.zwander.commonCompose.view.components.Page
import tk.zwander.commonCompose.view.keyCodeHandler
import java.awt.Desktop
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.event.WindowEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalComposeUiApi::class)
@ExperimentalTime
fun main() {
    BugsnagUtils.create()

    val exceptionHandlerFactory = WindowExceptionHandlerFactory { window ->
        WindowExceptionHandler { throwable ->
            throwable.printStackTrace()
            SwingUtilities.invokeLater {
                window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))

                BugsnagUtils.notify(throwable, Severity.ERROR)
                exitProcess(0)
            }
        }
    }

    System.setProperty("apple.laf.useScreenMenuBar", "true")
    System.setProperty("apple.awt.application.appearance", "system")
    System.setProperty("apple.awt.application.name", GradleConfig.appName)
    System.setProperty("compose.swing.render.on.graphics", "true")
    System.setProperty("compose.interop.blending", "true")

    SwingUtilities.invokeLater {
        if (HostOS.current == HostOS.MacOS) {
            UIManager.setLookAndFeel("org.violetlib.aqua.AquaLookAndFeel")
        }
    }

    when (HostOS.current) {
        HostOS.Linux -> {
            val context = try {
                DirectContext.makeGL()
            } catch (_: Throwable) {
                null
            }

            try {
                context?.flush()
            } catch (e: Throwable) {
                BugsnagUtils.notify(
                    IllegalStateException(
                        "Unable to flush OpenGL context, using software rendering.",
                        e
                    )
                )
                System.setProperty("skiko.renderApi", "SOFTWARE_FAST")
            } finally {
                try {
                    context?.close()
                } catch (_: Throwable) {
                }
            }
        }

        HostOS.Windows -> {
            if (HostArch.current == HostArch.EmulatedX64) {
                EventQueue.invokeAndWait {
                    val layer = SkiaLayer()
                    try {
                        layer.inDrawScope {
                            throw RenderException()
                        }
                    } catch (_: Throwable) {}

                    if (layer.renderApi == GraphicsApi.OPENGL) {
                        BugsnagUtils.notify(IllegalStateException("Skiko chose OpenGL on ARM, falling back to software rendering."))
                        System.setProperty("skiko.renderApi", "SOFTWARE_FAST")
                    }

                    layer.dispose()
                }
            }
        }

        else -> {}
    }

    application(
        exitProcessOnExit = false,
    ) {
        CompositionLocalProvider(
            LocalWindowExceptionHandlerFactory provides exceptionHandlerFactory,
        ) {
            val mainWindowState = rememberWindowState()
            val themeInfo = rememberThemeInfo()
            val scope = rememberCoroutineScope()
            val density = LocalDensity.current
            val pagerState = LocalPagerState.current

            val iconPainter = painterResource(Res.drawable.icon_rounded)

            DisposableEffect(null) {
                if (Desktop.getDesktop().isSupported(Desktop.Action.APP_ABOUT)) {
                    Desktop.getDesktop().setAboutHandler {
                        scope.launch {
                            pagerState.animateScrollToPage(Page.SettingsAbout)
                        }
                    }
                }

                onDispose {
                    if (Desktop.getDesktop().isSupported(Desktop.Action.APP_ABOUT)) {
                        Desktop.getDesktop().setAboutHandler(null)
                    }
                }
            }

            Window(
                onCloseRequest = ::exitApplication,
                title = GradleConfig.appName,
                icon = iconPainter,
                state = mainWindowState,
                onPreviewKeyEvent = keyCodeHandler(),
            ) {
                val useTransparency by BifrostSettings.useTransparencyEffects.collectAsState(false)

                val captionColor by remember {
                    derivedStateOf {
                        if (useTransparency) Color.Unspecified else themeInfo.colors.onBackground
                    }
                }
                val titleBarColor by remember {
                    derivedStateOf {
                        if (useTransparency) Color.Unspecified else themeInfo.colors.background
                    }
                }

                if (HostOS.current == HostOS.Windows) {
                    WindowStyle(
                        isDarkTheme = themeInfo.isDarkMode,
                        frameStyle = WindowFrameStyle(
                            borderColor = themeInfo.colors.background,
                            captionColor = captionColor,
                            titleBarColor = titleBarColor,
                        ),
                        backdropType = if (useTransparency) WindowBackdrop.Tabbed else WindowBackdrop.Transparent(Color.Transparent),
                    )

                    LaunchedEffect(captionColor, titleBarColor) {
                        if (isWindows11) {
                            DwmImpl.DwmSetWindowAttribute(
                                hwnd = window.hwnd,
                                attribute = DwmWindowAttribute.DWMWA_TEXT_COLOR.value,
                                value = IntByReference(captionColor.toBgr()),
                                valueSize = 4,
                            )
                            DwmImpl.DwmSetWindowAttribute(
                                hwnd = window.hwnd,
                                attribute = DwmWindowAttribute.DWMWA_CAPTION_COLOR.value,
                                value = IntByReference(titleBarColor.toBgr()),
                                valueSize = 4,
                            )
                        }
                    }
                }

                // For some reason this returns the title bar height on macOS.
                val menuBarHeight = remember(window.height) {
                    if (HostOS.current == HostOS.MacOS) window.height.dp else 0.dp
                }

                LaunchedEffect(window) {
                    window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                    window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)

                    val map = mutableMapOf<String, String>()

                    themeInfo.colors.primary.toAwtColor().let {
                        map.put(
                            "@accentColor",
                            String.format("#%06x", (it.rgb and 0xffffff)),
                        )
                    }
                    themeInfo.colors.background.toAwtColor().let {
                        map.put(
                            "@background",
                            String.format("#%06x", (it.rgb and 0xffffff)),
                        )
                    }

                    FlatLaf.setGlobalExtraDefaults(map)
                    FlatDarkLaf.setup()
                }

                LaunchedEffect(density) {
                    // Set this after getting the original height.
                    window.minimumSize = with(density) {
                        Dimension(200.dp.roundToPx(), 200.dp.roundToPx())
                    }
                }

                MacMenuBar(
                    mainWindowState = mainWindowState,
                    applicationScope = this@application,
                )

                CompositionLocalProvider(
                    LocalMenuBarHeight provides menuBarHeight,
                    LocalWindowDecorations provides LocalWindowDecorations.current.copy(top = menuBarHeight),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (HostOS.current == HostOS.MacOS) {
                            SwingPanel(
                                factory = {
                                    JPanel().apply {
                                        background = Color.Transparent.toAwtColor()
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = {
                                    it.putClientProperty(
                                        "Aqua.backgroundStyle",
                                        when {
                                            (OSVersion.current.major ?: 0) >= 26 -> "vibrantHUDWindow"
                                            themeInfo.isDarkMode -> "vibrantUltraDark"
                                            else -> "vibrantLight"
                                        },
                                    )
                                },
                            )
                        }

                        MainView(
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
    BugsnagUtils.destroy()
    exitProcess(0)
}
