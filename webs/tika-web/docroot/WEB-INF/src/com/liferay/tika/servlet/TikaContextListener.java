/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.tika.servlet;

import com.liferay.portal.kernel.bean.ClassLoaderBeanHandler;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.metadata.RawMetadataProcessor;
import com.liferay.portal.kernel.metadata.RawMetadataProcessorUtil;
import com.liferay.portal.kernel.portlet.PortletClassLoaderUtil;
import com.liferay.portal.kernel.util.BasePortalLifecycle;
import com.liferay.portal.kernel.util.File;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.MimeTypes;
import com.liferay.portal.kernel.util.MimeTypesUtil;
import com.liferay.portal.kernel.util.PortalClassLoaderUtil;
import com.liferay.portal.kernel.util.ProxyUtil;
import com.liferay.tika.metadata.TikaRawMetadataProcessor;
import com.liferay.tika.util.MimeTypesImpl;
import com.liferay.tika.util.TikaFileInvocationHandler;

import java.lang.reflect.InvocationHandler;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;

/**
 * @author Jonathan McCann
 */
public class TikaContextListener
	extends BasePortalLifecycle implements ServletContextListener {

	@Override
	public void contextDestroyed(ServletContextEvent servletContextEvent) {
		portalDestroy();
	}

	@Override
	public void contextInitialized(ServletContextEvent servletContextEvent) {
		registerPortalLifecycle();
	}

	@Override
	protected void doPortalDestroy() throws Exception {
		new FileUtil().setFile(_originalFile);

		new RawMetadataProcessorUtil().setRawMetadataProcessor(
			_originalRawMetadataProcessor);

		new MimeTypesUtil().setMimeTypes(_originalMimeTypesImpl);
	}

	@Override
	protected void doPortalInit() {
		ClassLoader portalClassLoader = PortalClassLoaderUtil.getClassLoader();
		ClassLoader portletClassLoader =
			PortletClassLoaderUtil.getClassLoader();

		try {
			_originalFile = FileUtil.getFile();

			File portalClassLoaderFile = (File)newInstance(
				portalClassLoader, File.class, _originalFile);

			TikaFileInvocationHandler fileImplInvocationHandler =
				new TikaFileInvocationHandler(portalClassLoaderFile);

			File portletClassLoaderFile = (File)newInstance(
					portletClassLoader, File.class, fileImplInvocationHandler);

			new FileUtil().setFile(portletClassLoaderFile);

			_originalRawMetadataProcessor =
				RawMetadataProcessorUtil.getRawMetadataProcessor();

			TikaRawMetadataProcessor tikaRawMetadataProcessor =
				new TikaRawMetadataProcessor();

			Parser parser = new AutoDetectParser(new TikaConfig());

			tikaRawMetadataProcessor.setParser(parser);

			RawMetadataProcessor rawMetadataProcessor =
				(RawMetadataProcessor)newInstance(
					portletClassLoader, RawMetadataProcessor.class,
					tikaRawMetadataProcessor);

			new RawMetadataProcessorUtil().setRawMetadataProcessor(
				rawMetadataProcessor);

			_originalMimeTypesImpl = MimeTypesUtil.getMimeTypes();

			MimeTypesImpl tikaMimeTypesImpl = new MimeTypesImpl();
			tikaMimeTypesImpl.afterPropertiesSet();

			MimeTypes mimeTypes = (MimeTypes)newInstance(
				portletClassLoader, MimeTypes.class, tikaMimeTypesImpl);

			new MimeTypesUtil().setMimeTypes(mimeTypes);
		}
		catch (Exception e) {
			_log.error("Cannot initialize Tika hook", e);
		}
	}

	protected Object newInstance(
			ClassLoader portletClassLoader, Class<?> interfaceClass,
			InvocationHandler invocationHandler)
		throws Exception {

		Class<?>[] interfaceClasses = new Class<?>[] { interfaceClass };

		Object instance = ProxyUtil.newProxyInstance(
			portletClassLoader, interfaceClasses, invocationHandler);

		return newInstance(portletClassLoader, interfaceClass, instance);
	}

	protected Object newInstance(
			ClassLoader portletClassLoader, Class<?> interfaceClass,
			Object instance)
		throws Exception {

		Class<?>[] interfaceClasses = new Class<?>[] { interfaceClass };

		return ProxyUtil.newProxyInstance(
			portletClassLoader, interfaceClasses,
			new ClassLoaderBeanHandler(instance, portletClassLoader));
	}

	private static Log _log = LogFactoryUtil.getLog(TikaContextListener.class);

	private File _originalFile;
	private MimeTypes _originalMimeTypesImpl;
	private RawMetadataProcessor _originalRawMetadataProcessor;

}