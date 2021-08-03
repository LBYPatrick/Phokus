package com.lbynet.Phokus.template;

public abstract class RotationListener {


    /**
     *
     * This method is called whenever new rotation data becomes available.
     *
     * The Following documentation is copied from android.hardware.SensorManager:
     *
     * @param azimuth Azimuth, angle of rotation about the -z axis.
     *                This value represents the angle between the device's y
     *                axis and the magnetic north pole. When facing north, this
     *                angle is 0, when facing south, this angle is &pi;.
     *                Likewise, when facing east, this angle is &pi;/2, and
     *                when` facing west, this angle is -&pi;/2. The range of
     *                values is -&pi; to &pi;.</li>
     * @param pitch   Pitch, angle of rotation about the x axis.
     *                This value represents the angle between a plane parallel
     *                to the device's screen and a plane parallel to the ground.
     *                Assuming that the bottom edge of the device faces the
     *                user and that the screen is face-up, tilting the top edge
     *                of the device toward the ground creates a positive pitch
     *                angle. The range of values is -&pi; to &pi;.</li>
     * @param roll    Roll, angle of rotation about the y axis. This
     *                value represents the angle between a plane perpendicular
     *                to the device's screen and a plane perpendicular to the
     *                ground. Assuming that the bottom edge of the device faces
     *                the user and that the screen is face-up, tilting the left
     *                edge of the device toward the ground creates a positive
     *                roll angle. The range of values is -&pi;/2 to &pi;/2.</li>
     */
    public abstract void onDataAvailable(float azimuth, float pitch, float roll);

}
