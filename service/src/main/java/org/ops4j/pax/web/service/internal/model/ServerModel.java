/* Copyright 2007 Alin Dreghiciu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.web.service.internal.model;

import java.util.Collection;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import org.osgi.service.http.HttpContext;
import org.ops4j.lang.NullArgumentException;

public class ServerModel
{

    private final Map<String, ServletModel> m_aliasMapping;
    private final Map<Servlet, ServletModel> m_servletModels;
    private final Map<Filter, FilterModel> m_filterModels;
    private final Map<EventListener, EventListenerModel> m_eventListenerModels;
    private final Map<HttpContext, ContextModel> m_contextModels;

    public ServerModel()
    {
        m_aliasMapping = new HashMap<String, ServletModel>();
        m_servletModels = new HashMap<Servlet, ServletModel>();
        m_filterModels = new HashMap<Filter, FilterModel>();
        m_eventListenerModels = new HashMap<EventListener, EventListenerModel>();
        m_contextModels = new HashMap<HttpContext, ContextModel>();
    }

    public synchronized ServletModel getServletModelWithAlias( final String alias )
    {
        NullArgumentException.validateNotEmpty( alias, "Alias" );
        return m_aliasMapping.get( alias );
    }

    public synchronized void addServletModel( final ServletModel model )
    {
        if( model.getAlias() != null )
        {
            m_aliasMapping.put( model.getAlias(), model );
        }
        m_servletModels.put( model.getServlet(), model );
        addContextModel( model.getContextModel() );
    }

    public synchronized void removeServletModel( final ServletModel model )
    {
        if( model.getAlias() != null )
        {
            m_aliasMapping.remove( model.getAlias() );
        }
        m_servletModels.remove( model.getServlet() );
    }

    public ServletModel removeServlet( final Servlet servlet )
    {
        final ServletModel model;
        synchronized( m_servletModels )
        {
            model = m_servletModels.get( servlet );
            if( model == null )
            {
                throw new IllegalArgumentException(
                    "Servlet [" + servlet + " is not currently registered in any context"
                );
            }
            m_servletModels.remove( servlet );
            return model;
        }
    }

    public void addEventListenerModel( final EventListenerModel model )
    {
        synchronized( m_eventListenerModels )
        {
            if( m_eventListenerModels.containsKey( model.getEventListener() ) )
            {
                throw new IllegalArgumentException( "Listener [" + model.getEventListener() + "] already registered." );
            }
            m_eventListenerModels.put( model.getEventListener(), model );
            addContextModel( model.getContextModel() );
        }
    }

    public EventListenerModel removeEventListener( final EventListener listener )
    {
        final EventListenerModel model;
        synchronized( m_eventListenerModels )
        {
            model = m_eventListenerModels.get( listener );
            if( model == null )
            {
                throw new IllegalArgumentException(
                    "Listener [" + listener + " is not currently registered in any context"
                );
            }
            m_eventListenerModels.remove( listener );
            return model;
        }
    }

    public void addFilterModel( final FilterModel model )
    {
        synchronized( m_filterModels )
        {
            if( m_filterModels.containsKey( model.getFilter() ) )
            {
                throw new IllegalArgumentException( "Filter [" + model.getFilter() + "] is already registered." );
            }
            m_filterModels.put( model.getFilter(), model );
            addContextModel( model.getContextModel() );
        }
    }

    public FilterModel removeFilter( final Filter filter )
    {
        final FilterModel model;
        synchronized( m_filterModels )
        {
            model = m_filterModels.get( filter );
            if( model == null )
            {
                throw new IllegalArgumentException(
                    "Filter [" + filter + " is not currently registered in any context"
                );
            }
            m_filterModels.remove( filter );
            return model;
        }
    }

    public ServletModel[] getServletModels()
    {
        final Collection<ServletModel> models = m_servletModels.values();
        return models.toArray( new ServletModel[models.size()] );
    }

    public EventListenerModel[] getEventListenerModels()
    {
        final Collection<EventListenerModel> models = m_eventListenerModels.values();
        return models.toArray( new EventListenerModel[models.size()] );
    }

    public FilterModel[] getFilterModels()
    {
        final Collection<FilterModel> models = m_filterModels.values();
        return models.toArray( new FilterModel[models.size()] );
    }

    public void addContextModel( final ContextModel contextModel )
    {
        if( !m_contextModels.containsKey( contextModel.getHttpContext() ) )
        {
            m_contextModels.put( contextModel.getHttpContext(), contextModel );
        }
    }

    public ContextModel[] getContextModels()
    {
        final Collection<ContextModel> contextModels = m_contextModels.values();
        if( contextModels == null || contextModels.size() == 0 )
        {
            return new ContextModel[0];
        }
        return contextModels.toArray( new ContextModel[contextModels.size()] );
    }

    public ContextModel getContextModel( final HttpContext httpContext )
    {
        return m_contextModels.get( httpContext );
    }

}