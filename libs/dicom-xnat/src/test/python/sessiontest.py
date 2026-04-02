#
# dicom-xnat: sessiontest.py
# XNAT http://www.xnat.org
# Copyright (c) 2017, Washington University School of Medicine
# All Rights Reserved
#
# Released under the Simplified BSD.
#
# Use DICOM data from an XNAT to run metadata translation tests
#

import difflib, errno, os, shutil, string, subprocess, sys


def ignore_non_dicom(dir, names):
    return [f for f in names if not
            (os.path.isdir(os.path.join(dir, f))
             or f.endswith('.dcm')
             or f in ['resources', 'subjects'])]

def ignore_dicom(dir, names):
    return [f for f in names if f.endswith('.dcm')]


def copy_dicom_tree(src, dst):
    """Copy only the DICOM files from src to dst."""
    # copy session to a study directory
    shutil.copytree(src, dst, ignore=ignore_non_dicom)
    # remove empty subdirectories
    for root, dirnames, filenames in os.walk(dst, topdown=False):
        if not dirnames and not filenames:
            os.rmdir(root)


def study_to_session(datadir, mxlibdir, dst):
    """Generate an XNAT session XML from a directory of DICOM files."""
    jars = []
    for root, dirnames, filenames in os.walk(mxlibdir):
        jars.extend([os.path.join(root, name)
                     for name in filenames if name.endswith('.jar')])
    classpath = string.join(jars, ':')
    subprocess.call(['java', '-cp', classpath,
                     'org.nrg.dcm.xnat.DICOMSessionBuilder',
                     datadir])
    try:
        shutil.copytree(datadir, dst, ignore=ignore_dicom)
        shutil.move(datadir+'.xml', dst)
    except OSError as exc:
        if errno.ENOENT == exc.errno:
            sys.stderr.write('unable to copy {}: {}\n'.format(
                    os.path.basename(datadir), exc.strerror));
        else:
            raise
    
def build_mxlib(mxlibdir,
                repodir='/tmp/mx-test-repos',
                src='https://bitbucket.org/nrg/dicom-xnat',
                rev='tip',
                name='dicom-xnat',
                testdata=None):
    """Build a lib directory for the requested revision of dicom-xnat-mx."""
    try:
        os.makedirs(repodir)
    except OSError as exc:
        if errno.EEXIST == exc.errno: pass
        else: raise
    dxsrcdir = os.path.join(repodir, name)
    with open(dxsrcdir+'-build.out', 'w') as buildout, \
            open(dxsrcdir+'-build.err', 'w') as builderr:
        if not os.path.isdir(dxsrcdir):
            subprocess.call(['hg', 'clone', '-r', rev, src, name],
                            stdout=buildout, stderr=builderr, cwd=repodir)
        mxsrcdir = os.path.join(dxsrcdir, 'dicom-xnat-mx')
        if testdata:
            testflag = '-DargLine=-Dsample.data.dir={}'.format(testdata)
        else:
            testflag = '-Dmaven.test.skip'
        subprocess.call(['mvn', 'clean', 'package', testflag],
                        stdout=buildout, stderr=builderr, cwd=dxsrcdir)
        subprocess.call(['mvn', 'dependency:copy-dependencies', testflag,
                         '-DoutputDirectory='+mxlibdir],
                        stdout=buildout, stderr=builderr, cwd=mxsrcdir)

    # Maven copy-dependencies doesn't get the build product,
    # so copy that now.
    for f in os.listdir(os.path.join(mxsrcdir, 'target')):
        path = os.path.join(mxsrcdir, 'target', f)
        if os.path.isfile(path):
            shutil.copy(path, mxlibdir)



def build_sessions(sessiondirs, revs,
                   data_cache=None,
                   clean_cache=False,
                   dst='.',
                   src={},
                   reporoot='/tmp/mx-test-repos',
                   libroot='/tmp/mx-lib',
                   buildtestdata=None,
                   verbose=False):
    """Build session XMLs for the provided session directories against
each of the named revisions."""
    for rev in revs:
        mxlibdir = os.path.join(libroot, rev)
        build_mxlib(mxlibdir,
                    repodir=reporoot,
                    src=src[rev] if rev in src \
                        else 'https://bitbucket.org/nrg/dicom-xnat',
                    rev=rev,
                    name='dicom-xnat-'+rev, testdata=buildtestdata)
    for sessiondir in sessiondirs:
        session = os.path.basename(sessiondir)
        if verbose: 
            print session+'...',
            sys.stdout.flush()
        if data_cache:
            copydir = os.path.join(data_cache, session)
            copy_dicom_tree(sessiondir, copydir)
            if verbose:
                print '(copied to '+copydir+')...',
                sys.stdout.flush()
        sessiondir = copydir
        for rev in revs:
            mxlibdir = os.path.join(libroot, rev)
            if verbose:
                print rev+'...',
                sys.stdout.flush()
            study_to_session(sessiondir, mxlibdir,
                             os.path.join(dst,session,rev))
        if copydir and clean_cache:
            try:
                shutil.rmtree(copydir)
            except OSError as exc:
                sys.stderr.write('unable to remove {}: {}\n'.format(
                        copydir, exc.strerror))
        if verbose:
            print 'done.'
            sys.stdout.flush()


def build_sessions_project_arc(revs, projectdir,
                               dst='.', src={},
                               data_cache=None, clean_cache=False,
                               reporoot='/tmp/mx-test-repos',
                               libroot='/tmp/mx-lib',
                               buildtestdata=None,
                               verbose=False):
    """Build session XMLs for all session directories in the provided
XNAT project archive directory."""
    sessiondirs = []
    for arc in os.listdir(projectdir):
        arcpath = os.path.join(projectdir, arc)
        if os.path.isdir(arcpath):
            for f in os.listdir(arcpath):
                path = os.path.join(arcpath, f)
                if os.path.isdir(path):
                    sessiondirs.append(path)
    if verbose:
        print 'Building translations for',len(sessiondirs),'sessions'
        sys.stdout.flush()

    build_sessions(sessiondirs, revs,
               data_cache=data_cache, clean_cache=clean_cache,
               dst=dst, src=src,
               reporoot=reporoot, libroot=libroot,
               buildtestdata=buildtestdata,
               verbose=verbose)


def compare_session_xml(sessionout, rev1, rev2):
    session = os.path.basename(sessiondir)
    [f1,f2] = [os.path.join(sessionout, rev, sessiondir+'.xml')
                for rev in [rev1, rev2]]
    [s1,s2] = [open(f).readlines() for f in [f1,f2]]
    diff = difflib.unified_diff(s1, s2, f1, f2)
    sys.stdout.write(diff)
