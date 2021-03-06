package application.prefs;

import java.util.Collection;

import org.daisy.braille.api.factory.FactoryProperties;

@FunctionalInterface
interface ListFactoryProperties {
	Collection<? extends FactoryProperties> list();
}