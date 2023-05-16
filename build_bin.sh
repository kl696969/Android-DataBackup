#!/bin/bash

# Tested on Ubuntu22.04.1(WSL2)

# Config
# $type: built-in, extend, all
# $abis: all or abis(armeabi-v7a, arm64-v8a, x86, x86_64) split by `,`
# `bash build_bin.sh $type $abis`
# e.g. `bash build_bin.sh built-in x86,x86_64`

# Whether use dev branch instead of release
ZSTD_DEV=false

NDK_VERSION=r25c

BIN_VERSION=1.4
ZLIB_VERSION=1.2.13            # https://zlib.net/
XZ_VERSION=5.4.3               # https://tukaani.org/xz/
LZ4_VERSION=1.9.4              # https://github.com/lz4/lz4/releases
ZSTD_VERSION=1.5.5             # https://github.com/facebook/zstd/releases
TAR_VERSION=1.34               # https://ftp.gnu.org/gnu/tar/?C=M;O=D
COREUTLS_VERSION=9.3           # https://ftp.gnu.org/gnu/coreutils/?C=M;O=D
TREE_VERSION=2.1.0             # https://mama.indstate.edu/users/ice/tree

EXTEND_VERSION=1.1.1
LIBFUSE_VERSION=3.12.0         # https://github.com/libfuse/libfuse/releases
RCLONE_VERSION=1.61.1          # https://github.com/rclone/rclone/releases

##################################################
# Functions
set_up_utils() {
    sudo apt-get update
    sudo apt-get install wget zip unzip bzip2 -q make gcc g++ clang meson golang-go cmake -y
    # Create build directory
    mkdir build_bin
    cd build_bin
    export LOCAL_PATH=$(pwd)
}

set_up_environment() {
    # Set build target
    export TARGET=aarch64-linux-android
    case "$TARGET_ARCH" in
    armeabi-v7a)
        export TARGET=armv7a-linux-androideabi
        ;;
    arm64-v8a)
        export TARGET=aarch64-linux-android
        ;;
    x86)
        export TARGET=i686-linux-android
        ;;
    x86_64)
        export TARGET=x86_64-linux-android
        ;;
    esac

    # NDK
    if [ ! -f $LOCAL_PATH/android-ndk-$NDK_VERSION-linux.zip ]; then
        wget https://dl.google.com/android/repository/android-ndk-$NDK_VERSION-linux.zip
    fi
    if [ -d $LOCAL_PATH/NDK ]; then
        rm -rf $LOCAL_PATH/NDK
    fi
    unzip -q android-ndk-$NDK_VERSION-linux.zip
    mv android-ndk-$NDK_VERSION NDK
    export NDK=$LOCAL_PATH/NDK
    export TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64
    export SYSROOT=$TOOLCHAIN/sysroot
    export API=28
    export AR=$TOOLCHAIN/bin/llvm-ar
    export CC=$TOOLCHAIN/bin/$TARGET$API-clang
    export AS=$CC
    export CXX=$TOOLCHAIN/bin/$TARGET$API-clang++
    export LD=$TOOLCHAIN/bin/ld
    export RANLIB=$TOOLCHAIN/bin/llvm-ranlib
    export STRIP=$TOOLCHAIN/bin/llvm-strip
    export BUILD_CFLAGS="-O3 -ffunction-sections -fdata-sections"
    export BUILD_LDFLAGS="-s -flto -Wl,--gc-sections"
    export BUILD_LDFLAGS_STATIC="-static $BUILD_LDFLAGS"
     
}

patch_gnu_symbols() {
    # $1: path
    sed -i "s/tzalloc/tzalloc_gnu/g" `grep tzalloc -rl $1`
    sed -i "s/tzfree/tzfree_gnu/g" `grep tzfree -rl ./`
    sed -i "s/localtime_rz/localtime_rz_gnu/g" `grep localtime_rz -rl $1`
    sed -i "s/mktime_z/mktime_z_gnu/g" `grep mktime_z -rl $1`
    sed -i "s/copy_file_range/copy_file_range_gnu/g" `grep copy_file_range -rl $1`
}

build_zlib() {
    # For zstd
    if [ ! -f $LOCAL_PATH/zlib-$ZLIB_VERSION.tar.gz ]; then
        wget https://zlib.net/zlib-$ZLIB_VERSION.tar.gz
    fi
    if [ -d $LOCAL_PATH/zlib-$ZLIB_VERSION ]; then
        rm -rf $LOCAL_PATH/zlib-$ZLIB_VERSION
    fi
    tar zxf zlib-$ZLIB_VERSION.tar.gz
    cd zlib-$ZLIB_VERSION
    ./configure --prefix=$SYSROOT
    make \
        AR=$AR \
        CC=$CC \
        AS=$AS \
        CXX=$CXX \
        LD=$LD \
        RANLIB=$RANLIB \
        STRIP=$STRIP \
        CFLAGS="$BUILD_CFLAGS" \
        CXXFLAGS="$BUILD_CFLAGS" \
        -j8
    make install -j8
    cd ..
    rm -rf zlib-$ZLIB_VERSION
}

build_liblzma() {
    # For zstd
    if [ ! -f $LOCAL_PATH/xz-$XZ_VERSION.tar.gz ]; then
        wget https://tukaani.org/xz/xz-$XZ_VERSION.tar.gz
    fi
    if [ -d $LOCAL_PATH/xz-$XZ_VERSION ]; then
        rm -rf $LOCAL_PATH/xz-$XZ_VERSION
    fi
    tar zxf xz-$XZ_VERSION.tar.gz
    cd xz-$XZ_VERSION
    ./configure --host=$TARGET --prefix=$SYSROOT CFLAGS="$BUILD_CFLAGS" CXXFLAGS="$BUILD_CFLAGS"
    make -j8 && make install -j8
    cd ..
    rm -rf xz-$XZ_VERSION
}

build_liblz4() {
    # For zstd
    if [ ! -f $LOCAL_PATH/v$LZ4_VERSION.zip ]; then
        wget https://github.com/lz4/lz4/archive/refs/tags/v$LZ4_VERSION.zip
    fi
    if [ -d $LOCAL_PATH/lz4-$LZ4_VERSION ]; then
        rm -rf $LOCAL_PATH/lz4-$LZ4_VERSION
    fi
    unzip -q v$LZ4_VERSION.zip
    cd lz4-$LZ4_VERSION
    make \
        AR=$AR \
        CC=$CC \
        AS=$AS \
        CXX=$CXX \
        LD=$LD \
        RANLIB=$RANLIB \
        STRIP=$STRIP \
        CFLAGS="$BUILD_CFLAGS" \
        CXXFLAGS="$BUILD_CFLAGS" \
        -j8
    make install prefix= DESTDIR=$SYSROOT
    cd ..
    rm -rf lz4-$LZ4_VERSION
}

build_zstd() {
    # Build needed libs
    build_zlib
    build_liblzma
    build_liblz4
    # Remove all shared libs
    rm -rf $SYSROOT/lib/*.so*
    rm -rf $SYSROOT/usr/lib/*/libz*
    rm -rf $SYSROOT/usr/lib/*/*/libz*

    if [ $ZSTD_DEV == true ]; then
        ZSTD_VERSION=dev
        if [ -d $LOCAL_PATH/zstd-$ZSTD_VERSION ]; then
            rm -rf $LOCAL_PATH/zstd-$ZSTD_VERSION
        fi
        git clone https://jihulab.com/XayahSuSuSu/zstd -b dev zstd-$ZSTD_VERSION
    else
        if [ ! -f $LOCAL_PATH/zstd-$ZSTD_VERSION.tar.gz ]; then
            wget https://github.com/facebook/zstd/releases/download/v$ZSTD_VERSION/zstd-$ZSTD_VERSION.tar.gz
        fi
        if [ -d $LOCAL_PATH/zstd-$ZSTD_VERSION ]; then
            rm -rf $LOCAL_PATH/zstd-$ZSTD_VERSION
        fi
        tar zxf zstd-$ZSTD_VERSION.tar.gz
    fi

    cd zstd-$ZSTD_VERSION/build/cmake
    mkdir builddir && cd builddir
    cmake \
    -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=$TARGET_ARCH \
    -DANDROID_NATIVE_API_LEVEL=$API \
    -DZSTD_BUILD_STATIC=ON \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX= \
    -DZSTD_MULTITHREAD_SUPPORT=ON \
    -DZSTD_ZLIB_SUPPORT=ON \
    -DZSTD_LZMA_SUPPORT=ON \
    -DZSTD_LZ4_SUPPORT=ON \
    -DZSTD_LEGACY_SUPPORT=OFF \
    -DCMAKE_EXE_LINKER_FLAGS="$BUILD_LDFLAGS_STATIC" \
    -DCMAKE_C_FLAGS="${CMAKE_C_FLAGS} $BUILD_CFLAGS" \
    -DCMAKE_CXX_FLAGS="${CMAKE_C_FLAGS} $BUILD_CFLAGS" \
    ..
    make -j8
    make install prefix= DESTDIR=$LOCAL_PATH/zstd

    $STRIP $LOCAL_PATH/zstd/bin/zstd
    cd ../../../..
    rm -rf zstd-$ZSTD_VERSION
}

build_tar() {
    if [ ! -f $LOCAL_PATH/tar-$TAR_VERSION.tar.xz ]; then
        wget https://ftp.gnu.org/gnu/tar/tar-$TAR_VERSION.tar.xz
    fi
    if [ -d $LOCAL_PATH/tar-$TAR_VERSION ]; then
        rm -rf tar-$TAR_VERSION
    fi
    tar xf tar-$TAR_VERSION.tar.xz
    cd tar-1.34

    # Patch duplicate symbols
    patch_gnu_symbols "gnu"

    ./configure --host=$TARGET LDFLAGS="$BUILD_LDFLAGS_STATIC" CFLAGS="$BUILD_CFLAGS -D_FORTIFY_SOURCE=0" CXXFLAGS="$BUILD_CFLAGS -D_FORTIFY_SOURCE=0"
    make -j8
    make install prefix= DESTDIR=$LOCAL_PATH/tar
    $STRIP $LOCAL_PATH/tar/bin/tar
    cd ..
    rm -rf tar-$TAR_VERSION
}

build_coreutls() {
    # df
    if [ ! -f $LOCAL_PATH/coreutils-$COREUTLS_VERSION.tar.xz ]; then
        wget https://ftp.gnu.org/gnu/coreutils/coreutils-$COREUTLS_VERSION.tar.xz
    fi
    if [ -d $LOCAL_PATH/coreutils-$COREUTLS_VERSION ]; then
        rm -rf coreutils-$COREUTLS_VERSION
    fi
    tar xf coreutils-$COREUTLS_VERSION.tar.xz
    cd coreutils-$COREUTLS_VERSION

    # Patch duplicate symbols
    patch_gnu_symbols "lib"

    ./configure --host=$TARGET LDFLAGS="$BUILD_LDFLAGS_STATIC" CFLAGS="$BUILD_CFLAGS -D_FORTIFY_SOURCE=0" CXXFLAGS="$BUILD_CFLAGS -D_FORTIFY_SOURCE=0"
    make -j8
    make install prefix= DESTDIR=$LOCAL_PATH/coreutls
    $STRIP $LOCAL_PATH/coreutls/bin/df
    cd ..
    rm -rf coreutils-$COREUTLS_VERSION
}

build_tree() {
    # tree
    if [ ! -f $LOCAL_PATH/tree-$TREE_VERSION.tgz ]; then
        wget https://mama.indstate.edu/users/ice/tree/src/tree-$TREE_VERSION.tgz
    fi
    if [ -d $LOCAL_PATH/tree-$TREE_VERSION ]; then
        rm -rf $LOCAL_PATH/tree-$TREE_VERSION
    fi
    tar xf tree-$TREE_VERSION.tgz
    cd tree-$TREE_VERSION

    echo "int strverscmp (const char *s1, const char *s2);" >> tree.h
    sed -i -e "/#ifndef __linux__/d" -e "/#endif/d" strverscmp.c

    make \
        AR=$AR \
        CC=$CC \
        AS=$AS \
        CXX=$CXX \
        LD=$LD \
        RANLIB=$RANLIB \
        STRIP=$STRIP \
        CFLAGS="$BUILD_CFLAGS" \
        CXXFLAGS="$BUILD_CFLAGS" \
        LDFLAGS="$BUILD_LDFLAGS_STATIC" \
        -j8
    make install prefix= DESTDIR=$LOCAL_PATH/tree
    $STRIP $LOCAL_PATH/tree/tree
    cd ..
    rm -rf tree-$TREE_VERSION
}

build_fusermount() {
    if [ ! -f $LOCAL_PATH/fuse-$LIBFUSE_VERSION.tar.xz ]; then
        wget https://github.com/libfuse/libfuse/releases/download/fuse-$LIBFUSE_VERSION/fuse-$LIBFUSE_VERSION.tar.xz
    fi
    if [ -d $LOCAL_PATH/fuse-$LIBFUSE_VERSION ]; then
        rm -rf fuse-$LIBFUSE_VERSION
    fi
    tar xf fuse-$LIBFUSE_VERSION.tar.xz
    cd fuse-$LIBFUSE_VERSION
    sed -i '/# Read build files from sub-directories/, $d' meson.build
    echo "subdir('util')" >> meson.build
    sed -i '/mount.fuse3/, $d' util/meson.build
    mkdir build && cd build

    export FUSE_HOST=aarch64
    case "$TARGET_ARCH" in
    armeabi-v7a)
        export FUSE_HOST=armv7a
        ;;
    arm64-v8a)
        export FUSE_HOST=aarch64
        ;;
    x86)
        export FUSE_HOST=i686
        ;;
    x86_64)
        export FUSE_HOST=x86_64
        ;;
    esac

    echo -e \
"[binaries]\n\
c = '$CC'\n\
cpp = '$CXX'\n\
ar = '$AR'\n\
ld = '$LD'\n\
strip = '$STRIP'\n\n\
[built-in options]\n\
c_args = ['-O3', '-ffunction-sections', '-fdata-sections']\n\
cpp_args = c_args\n\
c_link_args = ['-static', '-s', '-flto', '-Wl,--gc-sections']\n\
cpp_link_args = c_link_args\n\n\
[host_machine]\n\
system = 'android'\n\
cpu_family = '$FUSE_HOST'\n\
cpu = '$FUSE_HOST'\n\
endian = 'little'" > cross_config

    meson .. $FUSE_HOST --cross-file cross_config --prefix=/
    ninja -C $FUSE_HOST
    DESTDIR=$LOCAL_PATH/fuse ninja -C $FUSE_HOST install
    $STRIP $LOCAL_PATH/fuse/bin/fusermount3
    mv $LOCAL_PATH/fuse/bin/fusermount3 $LOCAL_PATH/fuse/bin/fusermount
    cd ../../
    rm -rf fuse-$LIBFUSE_VERSION
}

build_rclone() {
    if [ ! -f $LOCAL_PATH/rclone-v$RCLONE_VERSION.tar.gz ]; then
        wget https://github.com/rclone/rclone/releases/download/v$RCLONE_VERSION/rclone-v$RCLONE_VERSION.tar.gz
    fi
    if [ -d $LOCAL_PATH/rclone-v$RCLONE_VERSION ]; then
        rm -rf rclone-v$RCLONE_VERSION
    fi
    tar zxf rclone-v$RCLONE_VERSION.tar.gz
    cd rclone-v$RCLONE_VERSION

    export VAR_GOARCH=arm64
    case "$TARGET_ARCH" in
    armeabi-v7a)
        export VAR_GOARCH=arm
        ;;
    arm64-v8a)
        export VAR_GOARCH=arm64
        ;;
    x86)
        export VAR_GOARCH=386
        ;;
    x86_64)
        export VAR_GOARCH=amd64
        ;;
    esac

    # Static elf is not easy to build 'cause ndk doesn't provide static llog(liblog) which rlone needs.
    mkdir $LOCAL_PATH/rclone
    CGO_ENABLED=1 \
    CGO_CFLAGS="$CGO_CFLAGS $BUILD_CFLAGS" \
    CGO_CPPFLAGS="$CGO_CPPFLAGS $BUILD_CFLAGS" \
    CGO_LDFLAGS="$CGO_LDFLAGS $BUILD_LDFLAGS" \
    CC=$TOOLCHAIN/bin/$TARGET$API-clang GOOS=android GOARCH=$VAR_GOARCH \
    go build -o $LOCAL_PATH/rclone
    $STRIP $LOCAL_PATH/rclone/rclone
    cd ..
    rm -rf rclone-v$RCLONE_VERSION
}

build_built_in() {
    build_zstd
    build_tar
    build_coreutls
    build_tree
}

build_extend() {
    build_fusermount
    build_rclone
}

package_built_in() {
    # Built-in modules
    mkdir -p built_in/$TARGET_ARCH
    echo "$BIN_VERSION" > built_in/version
    zip -pj built_in/$TARGET_ARCH/bin coreutls/bin/df tar/bin/tar zstd/bin/zstd built_in/version tree/tree
    rm -rf ../app/src/$TARGET_ARCH/assets/bin/bin.zip
    cp built_in/$TARGET_ARCH/bin.zip ../app/src/$TARGET_ARCH/assets/bin/bin.zip
}

package_extend() {
    # Extend modules
    mkdir -p extend
    echo "$EXTEND_VERSION" > extend/version
    zip -pj extend/$TARGET_ARCH fuse/bin/fusermount rclone/rclone extend/version
    rm -rf ../extend/${TARGET_ARCH}.zip
    cp extend/${TARGET_ARCH}.zip ../extend/${TARGET_ARCH}.zip
}

build() {
    # $1: type
    case "$1" in
    built-in)
        build_built_in
        ;;
    extend)
        build_extend
        ;;
    *)
        build_built_in
        build_extend
        ;;
    esac
}

package() {
    # $1: type
    case "$1" in
    built-in)
        package_built_in
        ;;
    extend)
        package_extend
        ;;
    *)
        package_built_in
        package_extend
        ;;
    esac
}
##################################################

# Start to build
set_up_utils

if [[ $2 == all ]]; then
    abis=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")
else
    PRESERVED_IFS="$IFS"
    IFS=","
    abis=($2)
    IFS="$PRESERVED_IFS"
fi

for abi in ${abis[@]}; do
    TARGET_ARCH=$abi
    set_up_environment
    build $1
    package $1
    # Clean build files
    rm -rf NDK coreutls tar zstd fuse rclone tree
done
