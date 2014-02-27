# Generates the necessary JNI to use native audio libraries using JavaCPP.
# Call this from the project root.
if [ "$1" == "--build" ]; then
    # Build classes if we provide --build
    ./gradlew assembleDebug
fi

java -jar tools/javacpp-0.7.jar -cp build/classes/debug/ -d jni/ -nocompile com.morlunk.jumble.audio.javacpp.*

if [ "$1" == "--build" ]; then
    # Build native libs
    pushd jni
    ndk-build
    popd
fi
