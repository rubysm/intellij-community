// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BooleanTableCellEditor;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author stathik
 */
public class InstalledPluginsTableModel extends PluginTableModel {
  private static final InstalledPluginsState ourState = InstalledPluginsState.getInstance();

  private static final String ENABLED_DISABLED = "All plugins";
  private static final String ENABLED = "Enabled";
  private static final String DISABLED = "Disabled";
  private static final String BUNDLED = "Bundled";
  private static final String CUSTOM = "Custom";
  public static final String[] ENABLED_VALUES = {ENABLED_DISABLED, ENABLED, DISABLED, BUNDLED, CUSTOM};

  private final Map<PluginId, Boolean> myEnabled = ContainerUtil.newHashMap();
  private final Map<PluginId, Set<PluginId>> myDependentToRequiredListMap = ContainerUtil.newHashMap();
  private String myEnabledFilter = ENABLED_DISABLED;

  public InstalledPluginsTableModel() {
    final MyPluginManagerColumnInfo infoColumn = new MyPluginManagerColumnInfo();
    final EnabledPluginInfo enabledColumn = new EnabledPluginInfo();
    columns = new ColumnInfo[]{infoColumn, enabledColumn};

    final ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    for (IdeaPluginDescriptor plugin : PluginManagerCore.getPlugins()) {
      if (appInfo.isEssentialPlugin(plugin.getPluginId().getIdString())) {
        myEnabled.put(plugin.getPluginId(), true);
      }
      else {
        view.add(plugin);
      }
    }
    view.addAll(ourState.getInstalledPlugins());

    for (IdeaPluginDescriptor descriptor : view) {
      setEnabled(descriptor, descriptor.isEnabled());
    }
    updatePluginDependencies();

    setSortKey(new RowSorter.SortKey(getNameColumn(), SortOrder.ASCENDING));
  }

  public boolean hasProblematicDependencies(PluginId pluginId) {
    final Set<PluginId> ids = myDependentToRequiredListMap.get(pluginId);
    return ids != null && !ids.isEmpty();
  }

  @Nullable
  public Set<PluginId> getRequiredPlugins(PluginId pluginId) {
    return myDependentToRequiredListMap.get(pluginId);
  }

  public boolean isLoaded(PluginId pluginId) {
    return myEnabled.get(pluginId) != null;
  }

  public void appendOrUpdateDescriptor(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId id = descriptor.getPluginId();
    if (!PluginManager.isPluginInstalled(id)) {
      List<IdeaPluginDescriptor> list = isPluginDescriptorAccepted(descriptor) ? view : filtered;
      int i = list.indexOf(descriptor);
      if (i < 0) {
        list.add(descriptor);
      }
      else {
        list.set(i, descriptor);
      }

      setEnabled(descriptor, true);
      fireTableDataChanged();
    }
  }

  public static int getCheckboxColumn() {
    return 1;
  }

  @Override
  public int getNameColumn() {
    return 0;
  }

  private void setEnabled(IdeaPluginDescriptor ideaPluginDescriptor, boolean enabled) {
    final PluginId pluginId = ideaPluginDescriptor.getPluginId();
    if (!enabled && !PluginManagerCore.getDisabledPluginSet().contains(pluginId.toString())) {
      myEnabled.put(pluginId, null);
    }
    else {
      myEnabled.put(pluginId, enabled);
    }
  }

  public Map<PluginId, Set<PluginId>> getDependentToRequiredListMap() {
    return myDependentToRequiredListMap;
  }

  protected void updatePluginDependencies() {
    myDependentToRequiredListMap.clear();

    final int rowCount = getRowCount();
    for (int i = 0; i < rowCount; i++) {
      final IdeaPluginDescriptor descriptor = getObjectAt(i);
      final PluginId pluginId = descriptor.getPluginId();
      myDependentToRequiredListMap.remove(pluginId);
      if (descriptor instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)descriptor).isDeleted()) continue;
      final Boolean enabled = myEnabled.get(pluginId);
      if (enabled == null || enabled.booleanValue()) {
        PluginManagerCore.checkDependants(descriptor, pluginId1 -> PluginManager.getPlugin(pluginId1), dependantPluginId -> {
          final Boolean enabled1 = myEnabled.get(dependantPluginId);
          if ((enabled1 == null && !ourState.wasUpdated(dependantPluginId)) ||
              (enabled1 != null && !enabled1.booleanValue())) {
            Set<PluginId> required = myDependentToRequiredListMap.get(pluginId);
            if (required == null) {
              required = new HashSet<>();
              myDependentToRequiredListMap.put(pluginId, required);
            }

            required.add(dependantPluginId);
            //return false;
          }

          return true;
        }
        );
        if (enabled == null && !myDependentToRequiredListMap.containsKey(pluginId) && PluginManagerCore.isCompatible(descriptor)) {
          myEnabled.put(pluginId, true);
        }
      }
    }
  }

  @Override
  public void updatePluginsList(List<IdeaPluginDescriptor> list) {
    fireTableDataChanged();
  }

  public void enableRows(IdeaPluginDescriptor[] ideaPluginDescriptors, Boolean value) {
    for (IdeaPluginDescriptor ideaPluginDescriptor : ideaPluginDescriptors) {
      final PluginId currentPluginId = ideaPluginDescriptor.getPluginId();
      final Boolean enabled = myEnabled.get(currentPluginId) == null ? Boolean.FALSE : value;
      myEnabled.put(currentPluginId, enabled);
    }
    updatePluginDependencies();
    warnAboutMissedDependencies(value, ideaPluginDescriptors);
    hideNotApplicablePlugins(value, ideaPluginDescriptors);
  }

  private void hideNotApplicablePlugins(Boolean value, final IdeaPluginDescriptor... ideaPluginDescriptors) {
    if (!value && ENABLED.equals(myEnabledFilter) || (value && DISABLED.equals(myEnabledFilter))) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        for (IdeaPluginDescriptor ideaPluginDescriptor : ideaPluginDescriptors) {
          view.remove(ideaPluginDescriptor);
          filtered.add(ideaPluginDescriptor);
        }
        fireTableDataChanged();
      });
    }
  }

  public boolean isEnabled(final PluginId pluginId) {
    final Boolean enabled = myEnabled.get(pluginId);
    return enabled != null && enabled.booleanValue();
  }

  public boolean isDisabled(final PluginId pluginId) {
    final Boolean enabled = myEnabled.get(pluginId);
    return enabled != null && !enabled.booleanValue();
  }

  public Map<PluginId, Boolean> getEnabledMap() {
    return myEnabled;
  }

  public String getEnabledFilter() {
    return myEnabledFilter;
  }

  public void setEnabledFilter(String enabledFilter, String filter) {
    myEnabledFilter = enabledFilter;
    filter(filter);
  }

  @Override
  public boolean isPluginDescriptorAccepted(IdeaPluginDescriptor descriptor) {
    if (!myEnabledFilter.equals(ENABLED_DISABLED)) {
      final boolean enabled = isEnabled(descriptor.getPluginId());
      if (enabled && myEnabledFilter.equals(DISABLED)) return false;
      if (!enabled && myEnabledFilter.equals(ENABLED)) return false;
      final boolean bundled = descriptor.isBundled();
      if (bundled && myEnabledFilter.equals(CUSTOM)) return false;
      if (!bundled && myEnabledFilter.equals(BUNDLED)) return false;
    }
    return true;
  }

  private class EnabledPluginInfo extends ColumnInfo<IdeaPluginDescriptor, Boolean> {

    EnabledPluginInfo() {
      super(/*IdeBundle.message("plugin.manager.enable.column.title")*/"");
    }

    @Override
    public Boolean valueOf(IdeaPluginDescriptor ideaPluginDescriptor) {
      return myEnabled.get(ideaPluginDescriptor.getPluginId());
    }

    @Override
    public boolean isCellEditable(final IdeaPluginDescriptor ideaPluginDescriptor) {
      return true;
    }

    @Override
    public Class getColumnClass() {
      return Boolean.class;
    }

    @Override
    public TableCellEditor getEditor(final IdeaPluginDescriptor o) {
      return new BooleanTableCellEditor();
    }

    @Override
    public TableCellRenderer getRenderer(final IdeaPluginDescriptor ideaPluginDescriptor) {
      return new BooleanTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          return super.getTableCellRendererComponent(table, value == null ? Boolean.TRUE : value, isSelected, hasFocus, row, column);
        }
      };
    }

    @Override
    public void setValue(final IdeaPluginDescriptor ideaPluginDescriptor, Boolean value) {
      final PluginId currentPluginId = ideaPluginDescriptor.getPluginId();
      final Boolean enabled = myEnabled.get(currentPluginId) == null ? Boolean.FALSE : value;
      myEnabled.put(currentPluginId, enabled);
      updatePluginDependencies();
      warnAboutMissedDependencies(enabled, ideaPluginDescriptor);
      hideNotApplicablePlugins(value, ideaPluginDescriptor);
    }

    @Override
    public Comparator<IdeaPluginDescriptor> getComparator() {
      return (o1, o2) -> {
        final Boolean enabled1 = myEnabled.get(o1.getPluginId());
        final Boolean enabled2 = myEnabled.get(o2.getPluginId());
        if (enabled1 != null && enabled1.booleanValue()) {
          if (enabled2 != null && enabled2.booleanValue()) {
            return 0;
          }

          return 1;
        }
        else {
          if (enabled2 == null || !enabled2.booleanValue()) {
            return 0;
          }
          return -1;
        }
      };
    }
  }

  private void warnAboutMissedDependencies(final Boolean newEnabledState, final IdeaPluginDescriptor... descriptorsWithChangedEnabledState) {
    final Set<PluginId> deps = new HashSet<>();
    final List<IdeaPluginDescriptor> descriptorsToCheckDependencies = new ArrayList<>();
    if (newEnabledState) {
      Collections.addAll(descriptorsToCheckDependencies, descriptorsWithChangedEnabledState);
    } else {
      descriptorsToCheckDependencies.addAll(getAllPlugins());
      descriptorsToCheckDependencies.removeAll(Arrays.asList(descriptorsWithChangedEnabledState));

      for (Iterator<IdeaPluginDescriptor> iterator = descriptorsToCheckDependencies.iterator(); iterator.hasNext(); ) {
        IdeaPluginDescriptor descriptor = iterator.next();
        final Boolean enabled = myEnabled.get(descriptor.getPluginId());
        if (enabled == null || !enabled.booleanValue()) {
          iterator.remove();
        }
      }
    }

    for (final IdeaPluginDescriptor descriptorToCheckDependencies : descriptorsToCheckDependencies) {
      PluginManagerCore.checkDependants(descriptorToCheckDependencies, pluginId -> PluginManager.getPlugin(pluginId), dependencyPluginId -> {
        Boolean enabled = myEnabled.get(dependencyPluginId);
        if (enabled == null) {
          return false;
        }
        if (newEnabledState && !enabled.booleanValue()) {
          deps.add(dependencyPluginId);
        }

        if (!newEnabledState) {
          if (descriptorToCheckDependencies instanceof IdeaPluginDescriptorImpl &&
              ((IdeaPluginDescriptorImpl)descriptorToCheckDependencies).isDeleted()) {
            return true;
          }
          if (descriptorToCheckDependencies.isImplementationDetail()) return true;
          final PluginId pluginDescriptorId = descriptorToCheckDependencies.getPluginId();
          for (IdeaPluginDescriptor descriptor : descriptorsWithChangedEnabledState) {
            if (dependencyPluginId.equals(descriptor.getPluginId())) {
              deps.add(pluginDescriptorId);
              break;
            }
          }
        }
        return true;
      }
      );
    }
    if (!deps.isEmpty()) {
      final String listOfSelectedPlugins = StringUtil.join(descriptorsWithChangedEnabledState, pluginDescriptor -> pluginDescriptor.getName(), ", ");
      final Set<IdeaPluginDescriptor> pluginDependencies = new HashSet<>();
      final String listOfDependencies = StringUtil.join(deps, pluginId -> {
        final IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(pluginId);
        assert pluginDescriptor != null;
        pluginDependencies.add(pluginDescriptor);
        return pluginDescriptor.getName();
      }, "<br>");
      final String message = !newEnabledState ? "<html>The following plugins <br>" + listOfDependencies + "<br>are enabled and depend" +(deps.size() == 1 ? "s" : "") + " on selected plugins. " +
                                       "<br>Would you like to disable them too?</html>"
                                     : "<html>The following plugins on which " + listOfSelectedPlugins + " depend" + (descriptorsWithChangedEnabledState.length == 1 ? "s" : "") +
                                       " are disabled:<br>" + listOfDependencies + "<br>Would you like to enable them?</html>";
      if (Messages.showOkCancelDialog(message, newEnabledState ? "Enable Dependant Plugins" : "Disable Plugins with Dependency on this", Messages.getQuestionIcon()) == Messages.OK) {
        for (PluginId pluginId : deps) {
          myEnabled.put(pluginId, newEnabledState);
        }

        updatePluginDependencies();
        hideNotApplicablePlugins(newEnabledState, pluginDependencies.toArray(new IdeaPluginDescriptor[0]));
      }
    }
  }

  private class MyPluginManagerColumnInfo extends PluginManagerColumnInfo {
    MyPluginManagerColumnInfo() {
      super(PluginManagerColumnInfo.COLUMN_NAME, InstalledPluginsTableModel.this);
    }

    @Override
    public TableCellRenderer getRenderer(final IdeaPluginDescriptor pluginDescriptor) {
      return new PluginsTableRenderer(pluginDescriptor, false);
    }

    @Override
    protected boolean isSortByName() {
      return true;
    }

    @Override
    public Comparator<IdeaPluginDescriptor> getComparator() {
      final Comparator<IdeaPluginDescriptor> comparator = getColumnComparator();
      return (o1, o2) -> {
        if (isSortByStatus()) {
          final boolean incompatible1 = PluginManagerCore.isIncompatible(o1);
          final boolean incompatible2 = PluginManagerCore.isIncompatible(o2);
          if (incompatible1) {
            if (incompatible2) return comparator.compare(o1, o2);
            return -1;
          }
          if (incompatible2) return 1;

          final boolean hasNewerVersion1 = ourState.hasNewerVersion(o1.getPluginId());
          final boolean hasNewerVersion2 = ourState.hasNewerVersion(o2.getPluginId());
          if (hasNewerVersion1) {
            if (hasNewerVersion2) return comparator.compare(o1, o2);
            return -1;
          }
          if (hasNewerVersion2) return 1;


          final boolean wasUpdated1 = ourState.wasUpdated(o1.getPluginId());
          final boolean wasUpdated2 = ourState.wasUpdated(o2.getPluginId());
          if (wasUpdated1) {
            if (wasUpdated2) return comparator.compare(o1, o2);
            return -1;
          }
          if (wasUpdated2) return 1;


          if (o1 instanceof PluginNode) {
            if (o2 instanceof PluginNode) return comparator.compare(o1, o2);
            return -1;
          }
          if (o2 instanceof PluginNode) return 1;


          final boolean deleted1 = o1 instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)o1).isDeleted();
          final boolean deleted2 = o2 instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)o2).isDeleted();
          if (deleted1) {
            if (deleted2) return comparator.compare(o1, o2);
            return -1;
          }
          if (deleted2) return 1;

          final boolean enabled1 = isEnabled(o1.getPluginId());
          final boolean enabled2 = isEnabled(o2.getPluginId());
          if (enabled1 && !enabled2) return -1;
          if (enabled2 && !enabled1) return 1;
        }
        return comparator.compare(o1, o2);
      };
    }
  }
}
