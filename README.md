Overview
========

dicom-xnat is the parent project for bridging between the DICOM and
XNAT data models.

Project | Description
----- | -----
dicom-xnat-mx | translates metadata from a DICOM study into an XNAT experiment
dicom-xnat-sop | defines the relationship between DICOM SOPs and the XNAT data model
dicom-xnat-util | contains utility functions for DICOM data

Instructions
============

The JUnit tests provide test data in the repository. Building and testing the code no longer requires specifying a
separate sample data location. You can build the code and run the unit tests with the following command:

    mvn clean install
