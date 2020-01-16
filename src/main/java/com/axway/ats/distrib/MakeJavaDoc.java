package com.axway.ats.distrib;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.axway.ats.common.system.OperatingSystemType;
import com.axway.ats.common.systemproperties.AtsSystemProperties;
import com.axway.ats.action.processes.ProcessExecutor;
import com.axway.ats.core.utils.IoUtils;
import com.axway.ats.core.utils.StringUtils;

public class MakeJavaDoc {

    private static String JAVADOC_EXECUTABLE;

    static {

        final String jdkHome = getJDKFolder();

        JAVADOC_EXECUTABLE = IoUtils.normalizeDirPath( jdkHome ) + "bin"
                             + AtsSystemProperties.SYSTEM_FILE_SEPARATOR + "javadoc";
        if( OperatingSystemType.getCurrentOsType().isWindows() ) {
            JAVADOC_EXECUTABLE = JAVADOC_EXECUTABLE + ".exe";
        }

        if( !new File( JAVADOC_EXECUTABLE ).exists() ) {
            throw new RuntimeException( "javadoc executable not found at '" + JAVADOC_EXECUTABLE + "'" );
        }

        System.out.println( "javadoc executable found at " + JAVADOC_EXECUTABLE );
    }

    private String                JARS_SRC_DIR            = "";
    private String                THIRD_PARTY_JARS_DIR    = "";
    private String                JAVADOC_BEFORE_BASE_DIR = "";
    private String                JAVADOC_BASE_DIR        = "";
    private String                JAVADOC_SRC_DIR         = "";
    private String                JAVADOC_OPTIONS_FILE    = "";

    protected static final String LINE_SEPARATOR          = AtsSystemProperties.SYSTEM_LINE_SEPARATOR;
    protected static final String CLASSPATH_SEPARATOR     = File.pathSeparator;

    private List<String>          publicClasses           = new ArrayList<String>();
    private List<String>          classpathEntries        = new ArrayList<String>();

    public static void main( String[] args ) {

        //        BasicConfigurator.configure();

        MakeJavaDoc mjd = new MakeJavaDoc();
        mjd.readSrcDir();
        mjd.unzipAllClasses();
        mjd.collectClasspathEntries();
        mjd.collectPublicClasses( null );
        mjd.displayLists();
        mjd.fillJavadocOptionsFile();
        mjd.createJavadoc();

    }

    private void createJavadoc() {

        emptyLine();

        try {
            info( "Executing " + JAVADOC_EXECUTABLE + " - START" );
            info( "STD OUT FILE: " + JAVADOC_BASE_DIR + "result_std_out.txt" );
            info( "STD ERR FILE: " + JAVADOC_BASE_DIR + "result_std_err.txt" );

            ProcessExecutor pe = new ProcessExecutor( JAVADOC_EXECUTABLE,
                                                      new String[]{ "@" + JAVADOC_OPTIONS_FILE } );
            pe.setWorkDirectory( JAVADOC_BEFORE_BASE_DIR );
            pe.setStandardOutputFile( JAVADOC_BASE_DIR + "result_std_out.txt" );
            pe.setErrorOutputFile( JAVADOC_BASE_DIR + "result_std_err.txt" );
            pe.execute( true );
            info( "Executing " + JAVADOC_EXECUTABLE + " - END" );
        } catch( Exception e ) {
            error( "Error executing " + JAVADOC_EXECUTABLE, e );
            throw new RuntimeException( e );
        }
    }

    private void fillJavadocOptionsFile() {

        StringBuilder sb = new StringBuilder();

        // javadoc output folder
        sb.append( "-d javadoc/ready" );
        sb.append( LINE_SEPARATOR );

        // classpath
        boolean firstTime = true;
        sb.append( "-classpath " );
        for( String entry : classpathEntries ) {
            if( firstTime ) {
                firstTime = false;
            } else {
                sb.append( CLASSPATH_SEPARATOR );
            }
            sb.append( entry );
        }
        sb.append( LINE_SEPARATOR );

        sb.append( "-header \"<b>Technical documentation about the public API of ATS</b>" );
        sb.append( LINE_SEPARATOR );

        // java sources
        sb.append( "-sourcepath javadoc/src" );
        sb.append( LINE_SEPARATOR );

        for( String pub : publicClasses ) {
            sb.append( pub );
            sb.append( LINE_SEPARATOR );
        }

        emptyLine();
        info( "Writing " + JAVADOC_OPTIONS_FILE + " - START" );
        Writer output = null;
        try {
            output = new BufferedWriter( new FileWriter( new File( JAVADOC_OPTIONS_FILE ) ) );
            output.write( sb.toString() );
        } catch( Exception e ) {
            error( "Error writing " + JAVADOC_OPTIONS_FILE, e );
            throw new RuntimeException( "Error writing " + JAVADOC_OPTIONS_FILE );
        } finally {
            if( output != null ) {
                try {
                    output.close();
                } catch( IOException e ) {}
            }
        }

        info( "Writing " + JAVADOC_OPTIONS_FILE + " - END" );
    }

    private void displayLists() {

        emptyLine();
        info( "Found public classes - START" );
        for( String pub : publicClasses ) {
            info( pub );
        }
        info( "Found public classes - END" );
    }

    private void readSrcDir() {

        final String className = MakeJavaDoc.class.getName().replace( '.', '/' ) + ".class";
        final ClassLoader classLoader = MakeJavaDoc.class.getClassLoader();
        final URL classLocation = classLoader.getResource( className );

        String path = classLocation.getPath();
        path = path.substring( 0, path.indexOf( "classes" ) );

        JARS_SRC_DIR = path + "src_jars/";
        info( "JARS_SRC_DIR = " + JARS_SRC_DIR );

        THIRD_PARTY_JARS_DIR = path + "zipsrc/lib";
        info( "THIRD_PARTY_JARS_DIR = " + THIRD_PARTY_JARS_DIR );

        JAVADOC_BEFORE_BASE_DIR = path;
        info( "JAVADOC_BEFORE_BASE_DIR = " + JAVADOC_BEFORE_BASE_DIR );

        JAVADOC_BASE_DIR = path + "javadoc/";
        info( "JAVADOC_BASE_DIR = " + JAVADOC_BASE_DIR );

        JAVADOC_SRC_DIR = path + "javadoc/src/";
        info( "JAVADOC_SRC_DIR = " + JAVADOC_SRC_DIR );

        JAVADOC_OPTIONS_FILE = JAVADOC_BASE_DIR + "javadoc_options.txt";
        info( "JAVADOC_OPTIONS_FILE = " + JAVADOC_OPTIONS_FILE );

        cleanDstDir();
    }

    private void unzipAllClasses() {

        emptyLine();

        File[] files = new File( JARS_SRC_DIR ).listFiles();
        if( files == null ) {
            throw new RuntimeException( "Unable to retrieve the files from " + JARS_SRC_DIR );
        }

        for( File file : files ) {
            if( file == null ) {
                warn( "Null file object from " + JARS_SRC_DIR + ". We will skip it" );
                continue;
            }

            String fileName = file.getName();
            if( StringUtils.isNullOrEmpty( fileName ) ) {
                warn( "Null/Empty file name from " + JARS_SRC_DIR + ". We will skip it" );
                continue;
            }

            if( file.isFile() && fileName.startsWith( "ats-" ) && fileName.endsWith( ".jar" ) ) {
                unzipClasses( file );
            } else {
                info( "Skip " + fileName );
            }
        }
    }

    private void collectPublicClasses( File parentFile ) {

        if( parentFile == null ) {
            parentFile = new File( JAVADOC_SRC_DIR );
        }

        File[] childrenFiles;
        if( parentFile.isFile() ) {
            childrenFiles = new File[]{ parentFile };
        } else { // it is directory
            childrenFiles = parentFile.listFiles();
            if( childrenFiles == null ) {
                return;
            }
        }

        for( File childFile : childrenFiles ) {
            if( childFile.isDirectory() ) {
                for( File babyFile : childFile.listFiles() ) {
                    collectPublicClasses( babyFile );
                }
            } else {
                String fileName = childFile.getName();
                if( childFile.isFile() && fileName.endsWith( ".java" )
                    && !fileName.startsWith( "Internal" ) ) {
                    if( "package-info.java".equals( fileName ) ) {
                        publicClasses.add( getFullClassName( childFile.getPath() ) );
                    } else {
                        String fileContent = null;
                        try {
                            fileContent = IoUtils.streamToString( new FileInputStream( childFile ) );
                        } catch( Exception e ) {
                            error( "Error reading content of file: " + childFile.getAbsolutePath(), e );
                        }

                        if( fileContent != null ) {
                            String fullClassName = getFullClassName( childFile.getPath() );
                            if( fileContent.contains( "@PublicAtsApi" ) ) {
                                publicClasses.add( fullClassName );
                            }
                        }
                    }
                }
            }
        }
    }

    private void collectClasspathEntries() {

        File atsJarsFolder = new File( JARS_SRC_DIR );
        for( File file : atsJarsFolder.listFiles() ) {
            if( file.isFile() ) {
                classpathEntries.add( file.getPath() );
            }
        }

        File thirdPatryJarsFolder = new File( THIRD_PARTY_JARS_DIR );
        if( thirdPatryJarsFolder.exists() ) {
            for( File file : thirdPatryJarsFolder.listFiles() ) {
                classpathEntries.add( file.getPath() );
            }
        } else {
            error( THIRD_PARTY_JARS_DIR + " doesn't exist" );
        }
    }

    private String getFullClassName( String filePath ) {

        filePath = IoUtils.normalizeFilePath( filePath );

        String token = "src" + AtsSystemProperties.SYSTEM_FILE_SEPARATOR + "com";
        return JAVADOC_SRC_DIR + filePath.substring( filePath.indexOf( token ) + 4 );
    }

    private void unzipClasses( File jarFile ) {

        String fileName = jarFile.getPath();
        info( "Processing " + fileName + " - START" );

        try {
            JarFile jar = new JarFile( jarFile );
            Enumeration<JarEntry> enum1 = jar.entries();
            while( enum1.hasMoreElements() ) {
                JarEntry file = ( JarEntry ) enum1.nextElement();

                File f = new File( JAVADOC_SRC_DIR + file.getName() );
                if( file.isDirectory() ) {
                    f.mkdirs();
                    continue;
                } else {
                    // create the parent directory of the current file
                    File parentFile = f.getParentFile();
                    if (!parentFile.exists()) {
                        parentFile.mkdirs();
                    }
                }
                InputStream is = jar.getInputStream( file );
                FileOutputStream fos = new FileOutputStream( f );
                while( is.available() > 0 ) {
                    fos.write( is.read() );
                }
                fos.close();
                is.close();
            }

            jar.close();
        } catch( IOException ioe ) {
            error( "Error unziping: " + fileName, ioe );
            throw new RuntimeException( "Error unziping: " + fileName, ioe );
        }
        info( "Processing " + fileName + " - END" );
    }

    private void cleanDstDir() {

        emptyLine();
        info( "Cleaning the content of " + JAVADOC_BASE_DIR + " - START" );
        File file = new File( JAVADOC_BASE_DIR );
        if( !file.exists() ) {
            file.mkdirs();
        } else {
            File[] files = file.listFiles();
            if( files != null ) {
                for( File c : file.listFiles() ) {
                    deleteRecursively( c );
                }
            }
        }
        info( "Cleaning the content of " + JAVADOC_BASE_DIR + " - END" );
    }

    private void deleteRecursively( File file ) {

        if( file.isDirectory() ) {
            for( File c : file.listFiles() ) {
                deleteRecursively( c );
            }
        }

        if( !file.delete() ) {
            error( "Unable to delete: " + file.getAbsolutePath() );
        }
    }

    static String getJDKFolder() {

        final String javaHome = AtsSystemProperties.SYSTEM_JAVA_HOME_DIR;

        if( StringUtils.isNullOrEmpty( javaHome ) ) {
            throw new RuntimeException( "JAVA_HOME not set" );
        }

        // check if we point to JDK
        String javacExecutable = IoUtils.normalizeDirPath( javaHome ) + "bin"
                                 + AtsSystemProperties.SYSTEM_FILE_SEPARATOR + "javac";
        if( OperatingSystemType.getCurrentOsType().isWindows() ) {
            javacExecutable = javacExecutable + ".exe";
        }
        if( new File( javacExecutable ).exists() ) {
            return javaHome;
        }

        // maybe we point to a JRE inside a JDK
        // check if the parent folder is a JDK folder
        final String javaHomeParentFolder = new File( javaHome ).getParent();

        // check if we point to JDK
        javacExecutable = IoUtils.normalizeDirPath( javaHomeParentFolder ) + "bin"
                          + AtsSystemProperties.SYSTEM_FILE_SEPARATOR + "javac";
        if( OperatingSystemType.getCurrentOsType().isWindows() ) {
            javacExecutable = javacExecutable + ".exe";
        }
        if( new File( javacExecutable ).exists() ) {
            return javaHomeParentFolder;
        }

        throw new RuntimeException( "We cannot find the JDK folder, based on JAVA_HOME " + javaHome );
    }

    private void emptyLine() {

        info( "**************" );
    }

    private void info( String msg ) {

        System.out.println( msg );
    }

    private void warn( String msg ) {

        System.out.println( "[WARN]" + msg );
    }

    private void error( String msg ) {

        System.out.println( "[ERROR]" + msg );
    }

    private void error( String msg, Exception e ) {

        System.out.println( "[ERROR]" + msg );
        e.printStackTrace();
    }
}
