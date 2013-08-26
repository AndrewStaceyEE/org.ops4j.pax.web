package org.ops4j.pax.web.jsp;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.Filter;

import org.apache.jasper.security.SecurityUtil;

public class InstanceManager implements org.apache.tomcat.InstanceManager {

	private final Map<String, Map<String, String>> injectionMap = new HashMap<String, Map<String, String>>();

	private final Properties restrictedFilters = new Properties();
	private final Properties restrictedListeners = new Properties();
	private final Map<Class<?>, List<AnnotationCacheEntry>> annotationCache = new WeakHashMap<Class<?>, List<AnnotationCacheEntry>>();

	@Override
	public Object newInstance(String className) throws IllegalAccessException,
			InvocationTargetException, InstantiationException,
			ClassNotFoundException {
		ClassLoader classLoader = Thread.currentThread()
				.getContextClassLoader();
		Class<?> clazz = loadClassMaybePrivileged(className, classLoader);
		return newInstance(clazz.newInstance(), clazz);
	}

	@Override
	public Object newInstance(final String className,
			final ClassLoader classLoader) throws IllegalAccessException,
			InvocationTargetException, InstantiationException,
			ClassNotFoundException {
		Class<?> clazz = classLoader.loadClass(className);
		return newInstance(clazz.newInstance(), clazz);
	}

	@Override
	public void newInstance(Object o) throws IllegalAccessException,
			InvocationTargetException {
		newInstance(o, o.getClass());
	}

	private Object newInstance(Object instance, Class<?> clazz)
			throws IllegalAccessException, InvocationTargetException {
		Map<String, String> injections = injectionMap.get(clazz.getName());
		populateAnnotationsCache(clazz, injections);
		return instance;
	}

	@Override
	public void destroyInstance(Object instance) throws IllegalAccessException,
			InvocationTargetException {
		preDestroy(instance, instance.getClass());
	}

	/**
	 * Call preDestroy method on the specified instance recursively from deepest
	 * superclass to actual class.
	 * 
	 * @param instance
	 *            object to call preDestroy methods on
	 * @param clazz
	 *            (super) class to examine for preDestroy annotation.
	 * @throws IllegalAccessException
	 *             if preDestroy method is inaccessible.
	 * @throws java.lang.reflect.InvocationTargetException
	 *             if call fails
	 */
	protected void preDestroy(Object instance, final Class<?> clazz)
			throws IllegalAccessException, InvocationTargetException {
		Class<?> superClass = clazz.getSuperclass();
		if (superClass != Object.class) {
			preDestroy(instance, superClass);
		}

		// At the end the postconstruct annotated
		// method is invoked
		List<AnnotationCacheEntry> annotations = null;
		synchronized (annotationCache) {
			annotations = annotationCache.get(clazz);
		}
		if (annotations == null) {
			// instance not created through the instance manager
			return;
		}
		for (AnnotationCacheEntry entry : annotations) {
			if (entry.getType() == AnnotationCacheEntryType.PRE_DESTROY) {
				Method preDestroy = getMethod(clazz, entry);
				synchronized (preDestroy) {
					boolean accessibility = preDestroy.isAccessible();
					preDestroy.setAccessible(true);
					preDestroy.invoke(instance);
					preDestroy.setAccessible(accessibility);
				}
			}
		}
	}

	/**
	 * Make sure that the annotations cache has been populated for the provided
	 * class.
	 * 
	 * @param clazz
	 *            clazz to populate annotations for
	 * @param injections
	 *            map of injections for this class from xml deployment
	 *            descriptor
	 * @throws IllegalAccessException
	 *             if injection target is inaccessible
	 * @throws javax.naming.NamingException
	 *             if value cannot be looked up in jndi
	 * @throws java.lang.reflect.InvocationTargetException
	 *             if injection fails
	 */
	protected void populateAnnotationsCache(Class<?> clazz,
			Map<String, String> injections) throws IllegalAccessException,
			InvocationTargetException {

		while (clazz != null) {
			List<AnnotationCacheEntry> annotations = null;
			synchronized (annotationCache) {
				annotations = annotationCache.get(clazz);
			}
			if (annotations == null) {
				annotations = new ArrayList<AnnotationCacheEntry>();

				// Initialize methods annotations
				Method[] methods = null;
				methods = clazz.getDeclaredMethods();

				Method postConstruct = null;
				Method preDestroy = null;
				for (Method method : methods) {
					String methodName = method.getName();

					if (method.isAnnotationPresent(PostConstruct.class)) {
						if ((postConstruct != null)
								|| (method.getParameterTypes().length != 0)
								|| (Modifier.isStatic(method.getModifiers()))
								|| (method.getExceptionTypes().length > 0)
								|| (!method.getReturnType().getName()
										.equals("void"))) {
							throw new IllegalArgumentException(
									"Invalid PostConstruct annotation");
						}
						postConstruct = method;
					}

					if (method.isAnnotationPresent(PreDestroy.class)) {
						if ((preDestroy != null || method.getParameterTypes().length != 0)
								|| (Modifier.isStatic(method.getModifiers()))
								|| (method.getExceptionTypes().length > 0)
								|| (!method.getReturnType().getName()
										.equals("void"))) {
							throw new IllegalArgumentException(
									"Invalid PreDestroy annotation");
						}
						preDestroy = method;
					}
				}
				if (postConstruct != null) {
					annotations.add(new AnnotationCacheEntry(postConstruct
							.getName(), postConstruct.getParameterTypes(),
							null, AnnotationCacheEntryType.POST_CONSTRUCT));
				}
				if (preDestroy != null) {
					annotations.add(new AnnotationCacheEntry(preDestroy
							.getName(), preDestroy.getParameterTypes(), null,
							AnnotationCacheEntryType.PRE_DESTROY));
				}
				if (annotations.size() == 0) {
					// Use common empty list to save memory
					annotations = Collections.emptyList();
				}
				synchronized (annotationCache) {
					annotationCache.put(clazz, annotations);
				}
			}
			clazz = clazz.getSuperclass(); // CHECKSTYLE:SKIP
		}
	}

	/**
	 * Makes cache size available to unit tests.
	 */
	protected int getAnnotationCacheSize() {
		synchronized (annotationCache) {
			return annotationCache.size();
		}
	}

	protected Class<?> loadClassMaybePrivileged(final String className,
			final ClassLoader classLoader) throws ClassNotFoundException {
		Class<?> clazz;
		if (SecurityUtil.isPackageProtectionEnabled()) {
			try {
				clazz = AccessController
						.doPrivileged(new PrivilegedExceptionAction<Class<?>>() {

							@Override
							public Class<?> run() throws Exception {
								return classLoader.loadClass(className);
							}
						});
			} catch (PrivilegedActionException e) {
				Throwable t = e.getCause();
				if (t instanceof ClassNotFoundException) {
					throw (ClassNotFoundException) t;
				}
				throw new RuntimeException(t);
			}
		} else {
			clazz = classLoader.loadClass(className);
		}
		checkAccess(clazz);
		return clazz;
	}

	private void checkAccess(Class<?> clazz) {
		if (Filter.class.isAssignableFrom(clazz)) {
			checkAccess(clazz, restrictedFilters);
		} else {
			checkAccess(clazz, restrictedListeners);
		}
	}

	private void checkAccess(Class<?> clazz, Properties restricted) {
		while (clazz != null) {
			if ("restricted".equals(restricted.getProperty(clazz.getName()))) {
				throw new SecurityException("Restricted " + clazz);
			}
			clazz = clazz.getSuperclass(); // CHECKSTYLE:SKIP
		}

	}

	public static String getName(Method setter) {
		StringBuilder name = new StringBuilder(setter.getName());

		// remove 'set'
		name.delete(0, 3);

		// lowercase first char
		name.setCharAt(0, Character.toLowerCase(name.charAt(0)));

		return name.toString();
	}

	private static Method getMethod(final Class<?> clazz,
			final AnnotationCacheEntry entry) {
		Method result = null;

		try {
			result = clazz.getDeclaredMethod(entry.getAccessibleObjectName(),
					entry.getParamTypes());
		} catch (NoSuchMethodException e) {
			// Should never happen. On that basis don't log it.
		}
		return result;
	}

	private static Field getField(final Class<?> clazz,
			final AnnotationCacheEntry entry) {
		Field result = null;

		try {
			result = clazz.getDeclaredField(entry.getAccessibleObjectName());
		} catch (NoSuchFieldException e) {
			// Should never happen. On that basis don't log it.
		}
		return result;
	}

	private static final class AnnotationCacheEntry {
		private final String accessibleObjectName;
		private final Class<?>[] paramTypes;
		private final String name;
		private final AnnotationCacheEntryType type;

		public AnnotationCacheEntry(String accessibleObjectName,
				Class<?>[] paramTypes, String name,
				AnnotationCacheEntryType type) {
			this.accessibleObjectName = accessibleObjectName;
			this.paramTypes = paramTypes;
			this.name = name;
			this.type = type;
		}

		public String getAccessibleObjectName() {
			return accessibleObjectName;
		}

		public Class<?>[] getParamTypes() {
			return paramTypes;
		}

		public String getName() {
			return name;
		}

		public AnnotationCacheEntryType getType() {
			return type;
		}
	}

	private static enum AnnotationCacheEntryType {
		FIELD, SETTER, POST_CONSTRUCT, PRE_DESTROY
	}

}
