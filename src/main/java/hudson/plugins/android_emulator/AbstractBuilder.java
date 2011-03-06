package hudson.plugins.android_emulator;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.Builder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractBuilder extends Builder {

    /** Environment variable set by the plugin to specify the serial of the started AVD. */
    private static final String DEVICE_SERIAL_VARIABLE = "ANDROID_AVD_DEVICE";

    /**
     * Gets an Android SDK instance, ready for use.
     *
     * @param build The build for which we should retrieve the SDK instance.
     * @param launcher The launcher for the remote node.
     * @param listener The listener used to get the environment variables.
     * @return An Android SDK instance, or {@code null} if none was found.
     */
    protected static AndroidSdk getAndroidSdk(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        // Get configured, expanded Android SDK root value
        String androidHome = Utils.expandVariables(build, listener, Utils.getConfiguredAndroidHome());
        EnvVars envVars = Utils.getEnvironment(build, listener);

        // Retrieve actual SDK root based on given value
        String discoveredAndroidHome = Utils.discoverAndroidHome(launcher, envVars, androidHome);

        // Get Android SDK object from the given root (or locate on PATH)
        return Utils.getAndroidSdk(launcher, discoveredAndroidHome);
    }

    /**
     * Gets the Android device identifier for this job, defaulting to the AVD started by this plugin.
     *
     * @param build The build for which we should retrieve the SDK instance.
     * @param listener The listener used to get the environment variables.
     * @return The device identifier (defaulting to the value of "<tt>-s $ANDROID_AVD_DEVICE</tt>").
     */
    protected static String getDeviceIdentifier(AbstractBuild<?, ?> build, BuildListener listener) {
        String deviceIdentifier;
        String deviceSerialToken = String.format("$%s", DEVICE_SERIAL_VARIABLE);
        String deviceSerial = Utils.expandVariables(build, listener, deviceSerialToken);
        if (deviceSerial.equals(deviceSerialToken)) {
            // No emulator was started by this plugin; assume only one device is attached
            deviceIdentifier = "";
        } else {
            // Use the serial of the emulator started by this plugin
            deviceIdentifier = String.format("-s %s", deviceSerial);
        }

        return deviceIdentifier;
    }

    /**
     * Uninstalls the Android package corresponding to the given APK file from an Android device.
     *
     * @param build The build for which we should uninstall the package.
     * @param launcher The launcher for the remote node.
     * @param logger Where log output should be redirected to.
     * @param androidSdk The Android SDK to use.
     * @param deviceIdentifier The device from which the package should be removed.
     * @param apkPath The path to the APK file.
     * @throws IOException If execution failed.
     * @throws InterruptedException If execution failed.
     */
    protected static void uninstallApk(AbstractBuild<?, ?> build, Launcher launcher,
            PrintStream logger, AndroidSdk androidSdk, String deviceIdentifier, FilePath apkPath)
                throws IOException, InterruptedException {
        // Get package ID to uninstall
        String packageId = getPackageIdForApk(build, launcher, logger, androidSdk, apkPath);
        uninstallApk(build, launcher, logger, androidSdk, deviceIdentifier, packageId);
    }

    /**
     * Uninstalls the given Android package ID from the given Android device.
     *
     * @param build The build for which we should uninstall the package.
     * @param launcher The launcher for the remote node.
     * @param logger Where log output should be redirected to.
     * @param androidSdk The Android SDK to use.
     * @param deviceIdentifier The device from which the package should be removed.
     * @param packageId The ID of the Android package to remove from the given device.
     * @throws IOException If execution failed.
     * @throws InterruptedException If execution failed.
     */
    protected static void uninstallApk(AbstractBuild<?, ?> build, Launcher launcher,
            PrintStream logger, AndroidSdk androidSdk, String deviceIdentifier, String packageId)
                throws IOException, InterruptedException {
        // Execute uninstallation
        AndroidEmulator.log(logger, Messages.UNINSTALLING_APK(packageId));
        String adbArgs = String.format("%s uninstall %s", deviceIdentifier, packageId);
        Utils.runAndroidTool(launcher, logger, logger, androidSdk, Tool.ADB, adbArgs, null);
    }

    /**
     * Determines the package ID of an APK file.
     *
     * @param build The build for which we should uninstall the package.
     * @param launcher The launcher for the remote node.
     * @param logger Where log output should be redirected to.
     * @param androidSdk The Android SDK to use.
     * @param apkPath The path to the APK file.
     * @return The package ID for the given APK, or {@code null} if it could not be determined.
     * @throws IOException If execution failed.
     * @throws InterruptedException If execution failed.
     */
    private static String getPackageIdForApk(AbstractBuild<?, ?> build, Launcher launcher,
            PrintStream logger, AndroidSdk androidSdk, FilePath apkPath)
                throws IOException, InterruptedException {
        // Run aapt command on given APK
        ByteArrayOutputStream aaptOutput = new ByteArrayOutputStream();
        String args = String.format("dump badging %s", apkPath.getName());
        Utils.runAndroidTool(launcher, aaptOutput, logger, androidSdk, Tool.AAPT, args, apkPath.getParent());

        // Determine package ID from aapt output
        String packageId = null;
        String aaptResult = aaptOutput.toString();
        if (aaptResult.length() > 0) {
            Matcher matcher = Pattern.compile("package: +name='([^']+)'").matcher(aaptResult);
            if (matcher.find()) {
                packageId = matcher.group(1);
            }
        }

        return packageId;
    }

}
