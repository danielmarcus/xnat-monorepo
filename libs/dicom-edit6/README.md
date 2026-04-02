NRG DICOM Edit 6
================================

The NRG DICOM Edit 6 library provides support for extracting and modifying the
the DICOM header metadata and values in DICOM file sets.

Building
--------

To build NRG DICOM Edit 6, invoke Maven with the desired lifecycle phase.  For 
example, the following command cleans previous builds, builds a new jar file, 
creates archives containing the source code and JavaDocs for the library, runs
the library's unit tests, and installs the jar into the local repository:

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
mvn clean install
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Running
-------

DICOM Edit 6 is a library, but the build with dependencies provides a command-line interface tool. The build-with-dependencies can
be built from source above, or a recent runnable build can be download. Click on 'Downloads' and download 
the latest version of jar with dependencies. 

This can be run as:

~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
java -jar dicom-edit6-xxx-jar-with-dependencies.jar
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This will provide the usage info:

~~~
No scripts were specified.
anon -s <script-file>[,<lookup-file>] [-s <script-file>[,<lookup-file> ...] -i <path to input dicom file or dir> -o <path to output dir>
	Scripts will be processed in the order specified.
	Each script may optionally reference a lookup table.
	<script-file> may be a URL or file-path string.
~~~

Documentation for the script-file syntax: https://wiki.xnat.org/display/XTOOLS/DicomEdit+6.3+Language+Reference

