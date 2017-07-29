package org.jchmlib;

/**
 * Since enumeration has to check all CHM units,
 * it can be time consuming even when we do nothing in enumerator.
 * To end an enumeration earlier, throw {@link ChmStopEnumeration}.
 */
@SuppressWarnings("WeakerAccess")
public class ChmStopEnumeration extends Exception {

}
