<?php
/**
 * Sugar (PHP Template Engine)
 *
 * This file includes the core framework for Sugar, and auto-
 * includes all necessary sub-modules.
 *
 * PHP version 5
 *
 * LICENSE:
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @category  Template
 * @package   Sugar
 * @author    Sean Middleditch <sean@mojodo.com>
 * @author    Shawn Pearce
 * @copyright 2008-2010 Mojodo, Inc. and contributors
 * @license   http://opensource.org/licenses/mit-license.php MIT
 * @version   SVN: $Id: Sugar.php 340 2010-09-12 06:10:39Z Sean.Middleditch $
 * @link      http://php-sugar.net
 */

/**
 * Directory in which Sugar is installed.  Used for including
 * additional Sugar source files.
 * @global string Location of core Sugar package files.
 * @internal
 */
define('SUGAR_ROOT', dirname(__FILE__));

/**#@+
 * Core includes.
 */
require_once SUGAR_ROOT.'/Sugar/Exception.php';
require_once SUGAR_ROOT.'/Sugar/Context.php';
require_once SUGAR_ROOT.'/Sugar/Template.php';
require_once SUGAR_ROOT.'/Sugar/StorageDriver.php';
require_once SUGAR_ROOT.'/Sugar/CacheDriver.php';
include_once SUGAR_ROOT.'/Sugar/Runtime.php';
/**#@-*/

/**#@+
 * Drivers.
 */
require_once SUGAR_ROOT.'/Sugar/Storage/File.php';
require_once SUGAR_ROOT.'/Sugar/Storage/String.php';
require_once SUGAR_ROOT.'/Sugar/Cache/File.php';
/**#@-*/

/**
 * Utility routines.
 */
require_once SUGAR_ROOT.'/Sugar/Util.php';

/**
 * Sugar Standard Library.
 */
require_once SUGAR_ROOT.'/Sugar/Stdlib.php';

/**
 * Sugar core class.
 *
 * Instantiate this class to use Sugar.
 *
 * @category  Template
 * @package   Sugar
 * @author    Sean Middleditch <sean@mojodo.com>
 * @copyright 2008-2009 Mojodo, Inc. and contributors
 * @license   http://opensource.org/licenses/mit-license.php MIT
 * @version   Release: 0.83
 * @link      http://php-sugar.net
 */
class Sugar
{
    /**
     * Version of Sugar.
     */
    const VERSION = '0.83';

    /**
     * Passed to cache drivers to indicate that a compile cache is requested.
     */
    const CACHE_TPL = 'ctpl';

    /**
     * Passed to cache drivers to indicate that an HTML cache is requested.
     */
    const CACHE_HTML = 'chtml';

    /**
     * Causes all errors generated by Sugar templates to be printed to the user.
     * No indication of the error is returned to the calling script.  This is
     * the default behavior.
     */
    const ERROR_PRINT = 100;

    /**
     * Errors will be thrown as {@link Sugar_Exception} objects.
     */
    const ERROR_THROW = 101;

    /**
     * The error will be printed to the user, and then die() will be called to
     * terminate the script.
     */
    const ERROR_DIE = 102;

    /**
     * The error will be silently ignored.
     */
    const ERROR_IGNORE = 103;

    /**
     * All output will be escaped using htmlentities() with the
     * ENT_QUOTES flag set, using the {@link Sugar::$charset} setting.  This
     * is the default behavior.
     */
    const OUTPUT_HTML = 200;

    /**
     * Identical to {@link Sugar::OUTPUT_HTML}.
     */
    const OUTPUT_XHTML = 201;

    /**
     * All output will be escaped using htmlspecialchars() with the
     * ENT_QUOTES flag set, using the {@link Sugar::$charset} setting.  This
     * differs from {@link Sugar::OUTPUT_HTML} as only <, >, ", ', and & will
     * escaped.
     */
    const OUTPUT_XML = 202;

    /**
     * Disables all output escaping.
     */
    const OUTPUT_TEXT = 203;

    /**
     * Option code for setting or retrieving the charset used during encoding.
     *
     * The character set default is UTF-8.  Another popular value that some
     * applications may need is ISO-8859-1.
     */
    const CHARSET = 1;

    /**
     * Option code for setting or retrieving the output mode used during
     * escaping.
     *
     * The default output mode is {@link Sugar::OUTPUT_HTML}.  Other possible
     * values are {@link Sugar::OUTPUT_XHTML}, {@link Sugar::OUTPUT_XML}, and
     * {@link Sugar::OUTPUT_TEXT}.
     */
    const OUTPUT = 2;

    /**
     * Option code to for toggling debug mode.
     *
     * Debug mode is either true (enabled) or false (disabled).  It is
     * disabled by default.
     */
    const DEBUG = 3;

    /**
     * Option code to set or get the error handling mode used during script
     * execution.
     *
     * The default error handling mode is {@link Sugar::ERROR_PRINT}.  Other
     * possible values are {@link Sugar::ERROR_THROW},
     * {@link Sugar::ERROR_DIE}, and {@link Sugar::ERROR_IGNORE}.
     */
    const ERRORS = 4;

    /**
     * Cache expiration time in seconds.
     *
     * The default cache expiration time is 3600 seconds, or one hour.
     */
    const CACHE_LIMIT = 5;

    /**
     * Global variable context
     *
     * @var Sugar_Context
     */
    private $_globals;

    /**
     * Map of all registered functions.  The key is the function name,
     * and the value is an array containing the callback and function
     * flags.
     */
    private $_functions = array();

    /**
     * Map of all registered modifiers.  The key is the modifier name,
     * and the value is the callback.
     */
    private $_modifiers = array();

    /**
     * A map of storage drivers.  The key is the storage driver name,
     * and the value is the storage driver object.
     */
    private $_storage = array();

    /**
     * Cache management.  Used internally.
     *
     * @var Sugar_CacheHandler
     */
    public $cacheHandler = null;

    /**
     * Runtime engine.  Used internally.
     *
     * @var Sugar_Runtime
     */
    private $_runtime = null;

    /**
     * This is the cache driver to use for storing bytecode and HTML caches.
     * This is initialized to the {@link Sugar_Cache_File} driver by default.
     *
     * @var Sugar_CacheDriver
     */
    public $cache = null;

    /**
     * Setting this to true will disable all caching, forcing every template
     * to be recompiled and executed on every load.
     *
     * @var bool
     */
    public $debug = false;

    /**
     * This is the error handling method Sugar should use.  By default,
     * errors are echoed to the screen and no exceptions are thrown.  Set
     * this to one of the following:
     * - {@link Sugar::ERROR_THROW}: throw Sugar_Exception objects
     * - {@link Sugar::ERROR_PRINT}: print an error message (default)
     * - {@link Sugar::ERROR_DIE}: terminate the script
     * - {@link Sugar::ERROR_IGNORE}: do nothing
     *
     * @var int
     */
    public $errors;

    /**
     * This is the output escaping method to be used.  This is necessary
     * for many formats, such as XML and HTML, to ensure that special
     * are escaped properly.
     * - {@link Sugar::OUTPUT_HTML}: escape HTML special characters (default)
     * - {@link Sugar::OUTPUT_XHTML}: equivalent to self::OUTPUT_HTML
     * - {@link Sugar::OUTPUT_XML}: escapes XML special characters
     * - {@link Sugar::OUTPUT_TEXT}: no escaping is performed
     *
     * @var int
     */
    public $output;

    /**
     * This is the default storage driver to use when no storage driver
     * is specified as part of a template name.
     *
     * @var string
     */
    public $defaultStorage = 'file';

    /**
     * Maximum age of HTML caches in seconds.
     *
     * @var int
     */
    public $cacheLimit = 3600; // one hour

    /**
     * Directory in which templates can be found when using the file storage
     * driver.  This can either be a single string or an array.
     *
     * @var mixed
     */
    public $templateDir = './templates';

    /**
     * Directory in which bytecode and HTML caches can be stored when using
     * the file cache driver.
     *
     * @var string
     */
    public $cacheDir = './templates/cache';

    /**
     * Directory to search for plugins.  This can either be a single string or an array.
     *
     * @var mixed
     */
    public $pluginDir = './plugins';

    /**
     * Character set that output should be in.
     *
     * @var string
     */
    public $charset = 'UTF-8';

    /**
     * Opening delimiter character.
     *
     * @var string
     */
    public $delimStart = '{{';

    /**
     * Closing delimiter character.
     *
     * @var string
     */
    public $delimEnd = '}}';

    /**
     * Callback for checking method access.
     *
     * @var callback
     */
    public $methodAcl;

    /**
     * Constructor
     */
    public function __construct()
    { 
        $this->_storage ['file']= new Sugar_Storage_File($this);
        $this->_storage ['string']= new Sugar_Storage_String($this);
        $this->cache = new Sugar_Cache_File($this);
        $this->_runtime = new Sugar_Runtime($this);
        $this->_globals = new Sugar_Context(null, array());
        $this->errors = self::ERROR_PRINT;
        $this->output = self::OUTPUT_HTML;        
    }

    /**
     * Get the value of an option
     *
     * @param int   $option The option code to lookup.
     *
     * @return mixed The value to assign to the option.
     * @throws Sugar_Exception_Usage when an invalid option code is given
     * or an invalid value for the specific option is given.
     */
    public function getOption($code)
    {
        switch ($code) {
        case self::CHARSET:
            return $this->charset;
        case self::OUTPUT:
            return $this->output;
        case self::DEBUG:
            return $this->debug;
        case self::ERRORS:
            return $this->errors;
        case self::CACHE_LIMIT:
            return $this->cacheLimit;
        default:
            throw new Sugar_Exception_Usage("invalid option code: {$code}");
        }
    }

    /**
     * Set the value of an option
     *
     * @param int   $option The option code to lookup.
     * @param mixed $value  The value to assign to the option.
     *
     * @throws Sugar_Exception_Usage when an invalid option code is given
     * or an invalid value for the specific option is given.
     */
    public function setOption($code, $value)
    {
        switch ($code) {
        case self::CHARSET:
            return $this->charset = (string)$value;
        case self::OUTPUT:
            return $this->output = $value;
        case self::DEBUG:
            return $this->debug = (bool)$value;
        case self::ERRORS:
            return $this->errors = $value;
        case self::CACHE_LIMIT:
            return $this->cacheLimit = $value;
        default:
            throw new Sugar_Exception_Usage("invalid option code: {$code}");
        }
    }  

    /**
     * Set a new variable to be available within templates.
     *
     * @param string $name  The variable's name.
     * @param mixed  $value The variable's value.
     *
     * @return bool true on success
     */
    public function set($name, $value)
    {
        $this->_globals->set($name, $value);
        return true;
    }

    /**
     * Registers a new function to be available within templates.
     *
     * @param string   $name   The name to register the function under.
     * @param callback $invoke Optional PHP callback; if null, the $name
     *                         parameter is used as the callback.
     * @param bool     $cache  Whether the function is cacheable.
     * @param bool     $escape Whether the function output should be escaped.
     *
     * @return bool true on success
     */
    public function addFunction($name, $invoke = null, $cache = true, $escape = true)
    {
        if (!$invoke) {
            $invoke = 'sugar_function_'.strtolower($name);
        }

        $this->_functions [strtolower($name)]= array('name'=>$name,
                'invoke'=>$invoke, 'cache'=>$cache, 'escape'=>$escape);
        return true;
    }

    /**
     * Registers a new modifier to be available within templates.
     *
     * @param string   $name   The name to register the modifier under.
     * @param callback $invoke Optional PHP callback; if null, the $name
     *                         parameter is used as the callback.
     *
     * @return bool  true on success
     */
    public function addModifier($name, $invoke = null)
    {
        if (!$invoke) {
            $invoke = 'sugar_modifier_'.strtolower($name);
        }

        $this->_modifiers [strtolower($name)]= $invoke;
        return true;
    }

    /**
     * Looks up the current value of a variable.
     *
     * @param string $name Name of the variable to lookup.
     *
     * @return mixed
     */
    public function getVariable($name)
    {
        return $this->_globals->get($name);
    }

    /**
     * Returns an array containing the data for template function.  This
     * will first look for registered functions, then it will attempt to
     * auto-register a function using the smarty_function_foo naming
     * scheme.  Finally, it will attempt to load a function plugin.
     *
     * @param string $name Function name to lookup.
     *
     * @return array
     */
    public function getFunction($name)
    {
        $name = strtolower($name);

        // check for registered functions
        if (isset($this->_functions[$name])) {
            return $this->_functions[$name];
        }

        // try to auto-lookup the function
        $invoke = "sugar_function_$name";
        if (function_exists($invoke)) {
            return $this->_functions[$name] = array('name'=>$name,
                    'invoke'=>$invoke, 'cache'=>true, 'escape'=>true);
        }

        // attempt plugin loading
        $path = Sugar_Util_SearchForFile($this->pluginDir, $invoke.'.php');
        if ($path !== false) {
            require_once $path;
            if (function_exists($invoke)) {
                $this->_functions[$name] = array('name'=>$name,
                        'invoke'=>$invoke, 'cache'=>true, 'escape'=>true);
                return $this->_functions[$name];
            }
        }

        // nothing found
        return false;
    }

    /**
     * Returns the callback for a template modifier, if it exists.  This
     * will first look for registered modifiers, then it will attempt to
     * auto-register a modifier using the smarty_modifier_foo naming
     * scheme.  Finally, it will attempt to load a modifier plugin.
     *
     * @param string $name Modifier name to lookup.
     *
     * @return array
     */
    public function getModifier($name)
    {
        $name = strtolower($name);
        // check for registered modifiers
        if (isset($this->_modifiers[$name])) {
            return $this->_modifiers[$name];
        }

        // try to auto-lookup the modifier
        $invoke = "sugar_modifier_$name";
        if (function_exists($invoke)) {
            return $this->_modifiers[$name] = $invoke;
        }

        // attempt plugin loading
        $path = Sugar_Util_SearchForFile($this->pluginDir, $invoke.'.php');
        if ($path !== false) {
            require_once $path;
            if (function_exists($invoke)) {
                return $this->_modifiers[$name] = $invoke;
            }
        }

        // nothing found
        return false;
    }

    /**
     * Register a new storage driver.
     *
     * @param string        $name   Name to register driver under, used in
     *                              template references.
     * @param Sugar_StorageDriver $driver Driver object to register.
     *
     * @return bool true on success
     */
    public function addStorage($name, Sugar_StorageDriver $driver)
    {
        $this->_storage [$name]= $driver;
        return true;
    }

    /**
     * Get a storage driver.
     *
     * @param string $name Name of driver to look up.
     *
     * @return mixed Sugar_StorageDriver if found, null otherwise.
     */
    public function getStorage($name)
    {
        return isset($this->_storage[$name]) ? $this->_storage[$name] : null;
    }

    /**
     * Change the current delimiters.
     *
     * @param string $start Starting delimiter (default '{%')
     * @param string $end   Ending delimiter (default '%}')
     *
     * @return bool true on success
     */
    public function setDelimiter($start, $end)
    {
        $this->delimStart = $start;
        $this->delimEnd = $end;
        return true;
    }

    /**
     * Escape the input string according to the current value of
     * {@link Sugar::$charset}.
     *
     * @param string $string String to escape.
     *
     * @return string Escaped output.
     */
    public function escape($string)
    {
        // make sure this is a valid string
        $string = strval($string);

        // perform proper escaping for current mode
        switch ($this->output) {
        case self::OUTPUT_HTML:
            return htmlentities($string, ENT_COMPAT, $this->charset);
        case self::OUTPUT_XHTML:
        case self::OUTPUT_XML:
            return htmlspecialchars($string, ENT_QUOTES, $this->charset);
        case self::OUTPUT_TEXT:
        default:
            return $string;
        }
    }

    /**
     * Process an exception according to the current value of {@link Sugar::$errors}.
     *
     * @param Exception $e Exception to process.
     *
     * @return bool true on success
     */
    public function handleError(Exception $e)
    {
        // if in throw mode, re-throw the exception
        if ($this->errors == self::ERROR_THROW) {
            throw $e;
        }

        // if in ignore mode, just return
        if ($this->errors == self::ERROR_IGNORE) {
            return true;
        }

        // print the error
        echo "\n[[ ", $this->escape(get_class($e)), ': ',
                $this->escape($e->getMessage()), " ]]\n";

        // die if in die mode
        if ($this->errors == self::ERROR_DIE) {
            die();
        }

        return true;
    }

    /**
     * Get a runtime instance.
     *
     * @return Sugar_Runtime
     */
    public function getRuntime()
    {
        return $this->_runtime;
    }

    /**
     * Get the global variable context.
     *
     * @return Sugar_Runtime
     */
    public function getContext()
    {
        return $this->_globals;
    }

    /**
     * Load a template object
     *
     * @param string $name    Name of template to load
     * @param string $cacheId Optional cache ID for template
     *
     * @return Sugar_Template
     * @throws Sugar_Exception_Usage when the template cannot be found
     */
    public function getTemplate($name, $cacheId = null)
    {        
        $storageName = $this->defaultStorage;
        $baseName = $name;        

        // check for invalid storage type
        $storage = $this->getStorage($storageName);
        if (!$storage) {
            throw new Sugar_Exception_Usage('storage driver not found: '.$storageName);
        }

        // load driver, and check for handler
        $handle = $storage->getHandle($baseName);
        if ($handle === false) {
            throw new Sugar_Exception_Usage('template not found: '.$name);
        }
        if($storageName=='string'){
          $name = sha1($name);
        }
        // return new template object
        return new Sugar_Template($this, $storage, $handle, $name, $cacheId);
    }

    /**
     * Clear all HTML cache files.
     */
    public function clearCache()
    {
        $this->cache->clear();
    }

    /**
     * Load, compile, and display a template, caching the result.
     *
     * @param string $file    Template to display.
     * @param string $cacheId Cache identifier.
     * @param array  $vars    Additional vars to set during execution.
     * @param string $inherit Template to inherit from; overrides source.
     *
     * @return bool true on success.
     * @throws Sugar_Exception_Usage when the template name is invalid.
     *
     * @deprecated
     */
    public function displayCache($file, $cacheId, $vars = null, $inherit = null)
    {
        $template = $this->getTemplate($file, $cacheId);
        $template->setInherit($inherit);
        return $template->display(new Sugar_Context($template->getContext(), (array)$vars));
    }

    /**
     * Load, compile, and display the requested template.
     *
     * @param string $file    Template to display.
     * @param array  $vars    Additional vars to set during execution.
     * @param string $inherit Template to inherit from; overrides source.
     *
     * @return bool true on success.
     * @throws Sugar_Exception_Usage when the template name is invalid or
     * the template cannot be found.
     *
     * @deprecated
     */
    public function display($file, $vars = null, $inherit = null)
    {       
        return $this->displayCache($file, null, $vars, $inherit);
    }

    /**
     * Displays a cached template using {@link Sugar::displayCache}, but
     * returns the result as a string instead of displaying it to the user.
     *
     * @param string $file    Template to process.
     * @param string $cacheId Cache identifier.
     * @param array  $vars    Additional vars to set during execution.
     * @param string $inherit Template to inherit from; overrides source.
     *
     * @return string Template output.
     *
     * @deprecated
     */
    public function fetchCache($file, $cacheId, $vars = null, $inherit = null)
    {
        $template = $this->getTemplate($file, $cacheId);
        $template->setInherit($inherit);
        return $template->fetch(new Sugar_Context($template->getContext(), (array)$vars));
    }

    /**
     * Displays a template using {@link Sugar::display}, but returns
     * the result as a string instead of displaying it to the user.
     *
     * @param string $file    Template to process.
     * @param array  $vars    Additional vars to set during execution.
     * @param string $inherit Template to inherit from; overrides source.
     *
     * @return string Template output.
     *
     * @deprecated
     */
    public function fetch($file, $vars = null, $inherit = null)
    {        
        return $this->fetchCache($file, null, $vars, $inherit);
    }

    /**
     * Compile and display the template source code given as a string.
     *
     * It is recommended that this method be avoided in real applications,
     * as it can have drastic performance consequences.
     *
     * @param string $source Template code to display.
     * @param array  $vars   Additional vars to set during execution.
     *
     * @return bool true on success.
     *
     * @deprecated
     */
    function displayString($source, $vars = null)
    {
        $this->defaultStorage='string';
        return $this->display($source, $vars);
    }

    /**
     * Processes template source using {@link Sugar::displayString}, but
     * returns the result as a string instead of displaying it to the user.
     *
     * It is recommended that this method be avoided in real applications,
     * as it can have drastic performance consequences.
     *
     * @param string $source Template code to process.
     * @param array  $vars   Additional vars to set during execution.
     *
     * @return string Template output.
     *
     * @deprecated
     */
    public function fetchString($source, $vars = null)
    {  
      $this->defaultStorage='string';
      return $this->fetch($source, $vars);
    }

    /**
     * Check if a given template has a valid HTML cache.  If an HTML cache
     * already exists, applications can avoid expensive database queries
     * and other operations necessary to fill in template data.
     *
     * @param string $file    File to check.
     * @param string $cacheId Cache identifier.
     * @param array  $vars    Additional vars to set during execution.
     *
     * @return bool True if a valid HTML cache exists for the file.
     * @throws Sugar_Exception_Usage when the template name is invalid.
     *
     * @deprecated
     */
    function isCached($file, $cacheId, $vars = null)
    {
        // debug always disabled caching
        if ($this->debug) {
            return false;
        }

        // validate name
        $template = $this->getTemplate($file, $cacheId);
        return $template->isCached();
    }

    /**
     * Erases the HTML cache for a template if it exists.
     *
     * @param string $file    File to check.
     * @param string $cacheId Cache identifier.
     *
     * @throws Sugar_Exception_Usage when the template name is invalid.
     *
     * @deprecated
     */
    function uncache($file, $cacheId)
    {
        // erase the cache entry
        $template = $this->getTemplate($file, $cacheId);
        $this->cache->erase($template, self::CACHE_HTML);
    }
}
// vim: set expandtab shiftwidth=4 tabstop=4 :
?>
