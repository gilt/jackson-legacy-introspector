/**
 * 
 */
package net.bigpoint.jackson.databind.introspect;

import static net.bigpoint.jackson.databind.wrapper.AnnotationWrappingProxy.of;

import java.util.ArrayList;
import java.util.List;

import net.bigpoint.jackson.databind.wrapper.JsonDeserializer1To2Wrapper;
import net.bigpoint.jackson.databind.wrapper.JsonSerializer1To2Wrapper;
import net.bigpoint.jackson.databind.wrapper.KeyDeserializer1To2Wrapper;

import org.codehaus.jackson.annotate.JsonAnyGetter;
import org.codehaus.jackson.annotate.JsonAnySetter;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonBackReference;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonGetter;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonIgnoreType;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonManagedReference;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonPropertyOrder;
import org.codehaus.jackson.annotate.JsonRawValue;
import org.codehaus.jackson.annotate.JsonSetter;
import org.codehaus.jackson.annotate.JsonSubTypes;
import org.codehaus.jackson.annotate.JsonTypeName;
import org.codehaus.jackson.annotate.JsonValue;
import org.codehaus.jackson.annotate.JsonWriteNullProperties;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.KeyDeserializer;
import org.codehaus.jackson.map.annotate.JacksonInject;
import org.codehaus.jackson.map.annotate.JsonDeserialize;
import org.codehaus.jackson.map.annotate.JsonFilter;
import org.codehaus.jackson.map.annotate.JsonRootName;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonView;
import org.codehaus.jackson.map.annotate.NoClass;

import com.fasterxml.jackson.annotation.JsonFormat.Value;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize.Typing;
import com.fasterxml.jackson.databind.cfg.HandlerInstantiator;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.ObjectIdInfo;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.ser.std.RawSerializer;

/**
 * This introspector works with jackson 1 annotations. For custom Serializers/Deserializers and other configurable
 * classes this introspector will not return the class (as the default introspector would) but an instances. this is
 * necessary as all of those classes will need to be wrapped. This means that a custom {@link HandlerInstantiator} will
 * never be asked for an instance for those classes.
 * 
 * @author abaetz
 */
public class JacksonLegacyIntrospector extends NopAnnotationIntrospector {

	private static final long serialVersionUID = 1L;

	@Override
	public VisibilityChecker<?> findAutoDetectVisibility(AnnotatedClass ac, VisibilityChecker<?> checker) {
		JsonAutoDetect ann = ac.getAnnotation(JsonAutoDetect.class);
		return (ann == null) ? checker : checker.with(of(com.fasterxml.jackson.annotation.JsonAutoDetect.class, ann));
	}

	@Override
	public String findEnumValue(Enum<?> value) {
		return value.name();
	}

	@Override
	public Boolean findIgnoreUnknownProperties(AnnotatedClass ac) {
		JsonIgnoreProperties ignore = ac.getAnnotation(JsonIgnoreProperties.class);
		return (ignore == null) ? null : ignore.ignoreUnknown();
	}

	@Override
	public String[] findPropertiesToIgnore(Annotated ac) {
		JsonIgnoreProperties ignore = ac.getAnnotation(JsonIgnoreProperties.class);
		return (ignore == null) ? null : ignore.value();
	}

	/*
	/**********************************************************
	/* Serialization: property annotations
	/**********************************************************
	 */

	@Override
	public ReferenceProperty findReferenceType(AnnotatedMember member) {
		JsonManagedReference ref1 = member.getAnnotation(JsonManagedReference.class);
		if (ref1 != null) {
			return AnnotationIntrospector.ReferenceProperty.managed(ref1.value());
		}
		JsonBackReference ref2 = member.getAnnotation(JsonBackReference.class);
		if (ref2 != null) {
			return AnnotationIntrospector.ReferenceProperty.back(ref2.value());
		}
		return null;
	}

	@Override
	public PropertyName findRootName(AnnotatedClass ac) {
		JsonRootName ann = ac.getAnnotation(JsonRootName.class);
		return (ann == null) ? null : new PropertyName(ann.value());
	}

	@Override
	public Class<?> findDeserializationContentType(Annotated am, JavaType baseContentType) {
		JsonSerialize ann = am.getAnnotation(JsonSerialize.class);
		if (ann != null) {
			Class<?> cls = ann.contentAs();
			if (cls != NoClass.class) {
				return cls;
			}
		}
		return null;
	}

	@Override
	public Class<?> findDeserializationKeyType(Annotated am, JavaType baseKeyType) {
		// Primary annotation, JsonDeserialize
		JsonDeserialize ann = am.getAnnotation(JsonDeserialize.class);
		if (ann != null) {
			Class<?> cls = ann.keyAs();
			if (cls != NoClass.class) {
				return cls;
			}
		}
		return null;
	}

	@Override
	public Class<?> findDeserializationType(Annotated am, JavaType baseType) {
		// Primary annotation, JsonDeserialize
		JsonDeserialize ann = am.getAnnotation(JsonDeserialize.class);
		if (ann != null) {
			Class<?> cls = ann.as();
			if (cls != NoClass.class) {
				return cls;
			}
		}
		return null;
	}

	@Override
	public PropertyName findNameForDeserialization(Annotated a) {
		// [Issue#69], need bit of delegation
		String name;
		if (a instanceof AnnotatedField) {
			name = findDeserializationName((AnnotatedField) a);
		} else if (a instanceof AnnotatedMethod) {
			name = findDeserializationName((AnnotatedMethod) a);
		} else if (a instanceof AnnotatedParameter) {
			name = findDeserializationName((AnnotatedParameter) a);
		} else {
			name = null;
		}
		if (name != null) {
			if (name.length() == 0) { // empty String means 'default'
				return PropertyName.USE_DEFAULT;
			}
			return new PropertyName(name);
		}
		return null;
	}

	@Override
	public String findDeserializationName(AnnotatedField af) {
		JsonProperty pann = af.getAnnotation(JsonProperty.class);
		if (pann != null) {
			return pann.value();
		}
		// Also: having JsonDeserialize implies it is such a property
		// 09-Apr-2010, tatu: Ditto for JsonView
		if (af.hasAnnotation(JsonDeserialize.class) || af.hasAnnotation(JsonView.class)
				|| af.hasAnnotation(JsonBackReference.class) || af.hasAnnotation(JsonManagedReference.class)) {
			return "";
		}
		return null;
	}

	@Override
	public String findDeserializationName(AnnotatedMethod am) {
		// @JsonSetter has precedence over @JsonProperty, being more specific
		JsonSetter ann = am.getAnnotation(JsonSetter.class);
		if (ann != null) {
			return ann.value();
		}
		JsonProperty pann = am.getAnnotation(JsonProperty.class);
		if (pann != null) {
			return pann.value();
		}
		// @JsonSerialize implies that there is a property, but no name
		// 09-Apr-2010, tatu: Ditto for JsonView
		// 19-Oct-2011, tatu: And JsonBackReference/JsonManagedReference
		if (am.hasAnnotation(JsonDeserialize.class) || am.hasAnnotation(JsonView.class)
				|| am.hasAnnotation(JsonBackReference.class) || am.hasAnnotation(JsonManagedReference.class)) {
			return "";
		}
		return null;
	}

	@Override
	public String findDeserializationName(AnnotatedParameter param) {
		if (param != null) {
			JsonProperty pann = param.getAnnotation(JsonProperty.class);
			if (pann != null) {
				return pann.value();
			}
			/* And can not use JsonDeserialize as we can not use
			 * name auto-detection (names of local variables including
			 * parameters are not necessarily preserved in bytecode)
			 */
		}
		return null;
	}

	@Override
	public Class<?> findSerializationContentType(Annotated am, JavaType baseType) {
		JsonSerialize ann = am.getAnnotation(JsonSerialize.class);
		if (ann != null) {
			Class<?> cls = ann.contentAs();
			if (cls != NoClass.class) {
				return cls;
			}
		}
		return null;
	}

	@SuppressWarnings("deprecation")
	@Override
	public Include findSerializationInclusion(Annotated a, Include defValue) {
		JsonSerialize ann = a.getAnnotation(JsonSerialize.class);
		if (ann != null) {
			return Include.valueOf(ann.include().name());
		}
		/* 23-May-2009, tatu: Will still support now-deprecated (as of 1.1)
		 *   legacy annotation too:
		 */
		JsonWriteNullProperties oldAnn = a.getAnnotation(JsonWriteNullProperties.class);
		if (oldAnn != null) {
			boolean writeNulls = oldAnn.value();
			return writeNulls ? Include.ALWAYS : Include.NON_NULL;
		}
		return defValue;
	}

	@Override
	public Class<?> findSerializationKeyType(Annotated am, JavaType baseType) {
		JsonSerialize ann = am.getAnnotation(JsonSerialize.class);
		if (ann != null) {
			Class<?> cls = ann.keyAs();
			if (cls != NoClass.class) {
				return cls;
			}
		}
		return null;
	}

	@Override
	public String[] findSerializationPropertyOrder(AnnotatedClass ac) {
		JsonPropertyOrder order = ac.getAnnotation(JsonPropertyOrder.class);
		return (order == null) ? null : order.value();
	}

	@Override
	public Boolean findSerializationSortAlphabetically(AnnotatedClass ac) {
		JsonPropertyOrder order = ac.getAnnotation(JsonPropertyOrder.class);
		return (order == null) ? null : order.alphabetic();
	}

	@Override
	public Class<?> findSerializationType(Annotated a) {
		JsonSerialize ann = a.getAnnotation(JsonSerialize.class);
		if (ann != null) {
			Class<?> cls = ann.as();
			if (cls != NoClass.class) {
				return cls;
			}
		}
		return null;
	}

	@Override
	public Typing findSerializationTyping(Annotated a) {
		JsonSerialize ann = a.getAnnotation(JsonSerialize.class);
		return (ann == null) ? null : Typing.valueOf(ann.typing().name());
	}

	@Override
	public PropertyName findNameForSerialization(Annotated a) {
		// [Issue#69], need bit of delegation
		String name;
		if (a instanceof AnnotatedField) {
			name = findSerializationName((AnnotatedField) a);
		} else if (a instanceof AnnotatedMethod) {
			name = findSerializationName((AnnotatedMethod) a);
		} else {
			name = null;
		}
		if (name != null) {
			if (name.length() == 0) { // empty String means 'default'
				return PropertyName.USE_DEFAULT;
			}
			return new PropertyName(name);
		}
		return null;
	}

	@Override
	public String findSerializationName(AnnotatedField af) {
		JsonProperty pann = af.getAnnotation(JsonProperty.class);
		if (pann != null) {
			return pann.value();
		}
		// Also: having JsonSerialize implies it is such a property
		// 09-Apr-2010, tatu: Ditto for JsonView
		if (af.hasAnnotation(JsonSerialize.class) || af.hasAnnotation(JsonView.class)) {
			return "";
		}
		return null;
	}

	@SuppressWarnings("deprecation")
	@Override
	public String findSerializationName(AnnotatedMethod am) {
		// @JsonGetter is most specific, has precedence
		JsonGetter ann = am.getAnnotation(JsonGetter.class);
		if (ann != null) {
			return ann.value();
		}
		JsonProperty pann = am.getAnnotation(JsonProperty.class);
		if (pann != null) {
			return pann.value();
		}
		/* 22-May-2009, tatu: And finally, JsonSerialize implies
		 *   that there is a property, although doesn't define name
		 */
		// 09-Apr-2010, tatu: Ditto for JsonView
		if (am.hasAnnotation(JsonSerialize.class) || am.hasAnnotation(JsonView.class)) {
			return "";
		}
		return null;
	}

	@Override
	public boolean hasAnyGetterAnnotation(AnnotatedMethod am) {
		/* No dedicated disabling; regular @JsonIgnore used
		 * if needs to be ignored (handled separately
		 */
		return am.hasAnnotation(JsonAnyGetter.class);
	}

	@Override
	public boolean hasAnySetterAnnotation(AnnotatedMethod am) {
		/* No dedicated disabling; regular @JsonIgnore used
		 * if needs to be ignored (and if so, is handled prior
		 * to this method getting called)
		 */
		return am.hasAnnotation(JsonAnySetter.class);
	}

	@Override
	public boolean hasAsValueAnnotation(AnnotatedMethod am) {
		JsonValue ann = am.getAnnotation(JsonValue.class);
		// value of 'false' means disabled...
		return (ann != null && ann.value());
	}

	@Override
	public boolean hasCreatorAnnotation(Annotated a) {
		/* No dedicated disabling; regular @JsonIgnore used
		 * if needs to be ignored (and if so, is handled prior
		 * to this method getting called)
		 */
		return a.hasAnnotation(JsonCreator.class);
	}

	@Override
	public boolean hasIgnoreMarker(AnnotatedMember m) {
		JsonIgnore ann = m.getAnnotation(JsonIgnore.class);
		return (ann != null && ann.value());
	}

	@Override
	public Boolean isIgnorableType(AnnotatedClass ac) {
		JsonIgnoreType ignore = ac.getAnnotation(JsonIgnoreType.class);
		return (ignore == null) ? null : ignore.value();
	}

	@Override
	public String findTypeName(AnnotatedClass ac) {
		JsonTypeName tn = ac.getAnnotation(JsonTypeName.class);
		return (tn == null) ? null : tn.value();
	}

	@Override
	public List<NamedType> findSubtypes(Annotated a) {
		JsonSubTypes t = a.getAnnotation(JsonSubTypes.class);
		if (t == null) {
			return null;
		}
		JsonSubTypes.Type[] types = t.value();
		ArrayList<NamedType> result = new ArrayList<NamedType>(types.length);
		for (JsonSubTypes.Type type : types) {
			result.add(new NamedType(type.value(), type.name()));
		}
		return result;
	}

	/*
	 * Some impossible stuff
	 */
	@Override
	public Boolean hasRequiredMarker(AnnotatedMember m) {
		return super.hasRequiredMarker(m);
	}

	@Override
	public Object findNamingStrategy(AnnotatedClass ac) {
		return super.findNamingStrategy(ac);
	}

	@Override
	public Boolean isTypeId(AnnotatedMember member) {
		return super.isTypeId(member);
	}

	@Override
	public Value findFormat(Annotated memberOrClass) {
		return super.findFormat(memberOrClass);
	}

	@Override
	@Deprecated
	public Value findFormat(AnnotatedMember member) {
		return super.findFormat(member);
	}

	@Override
	public ObjectIdInfo findObjectIdInfo(Annotated ann) {
		return super.findObjectIdInfo(ann);
	}

	@Override
	public ObjectIdInfo findObjectReferenceInfo(Annotated ann, ObjectIdInfo objectIdInfo) {
		return super.findObjectReferenceInfo(ann, objectIdInfo);
	}

	@Override
	public Class<?> findPOJOBuilder(AnnotatedClass ac) {
		return super.findPOJOBuilder(ac);
	}

	@Override
	public com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder.Value findPOJOBuilderConfig(AnnotatedClass ac) {
		return super.findPOJOBuilderConfig(ac);
	}

	/*
	 * Some yet unimplemented stuff
	 */
	@Override
	public Class<?>[] findViews(Annotated a) {
		return super.findViews(a);
	}

	@Override
	public PropertyName findWrapperName(Annotated ann) {
		return super.findWrapperName(ann);
	}

	/*
	 * handling of serializers
	 */
	@Override
	public Object findSerializer(Annotated am) {
		/* 21-May-2009, tatu: Slight change; primary annotation is now
		 *    @JsonSerialize; @JsonUseSerializer is deprecated
		 */
		JsonSerialize ann = am.getAnnotation(JsonSerialize.class);
		if (ann != null) {
			Class<? extends JsonSerializer<?>> serClass = ann.using();
			if (serClass != JsonSerializer.None.class) {
				try {
					return new JsonSerializer1To2Wrapper<Object>((JsonSerializer<Object>) serClass.newInstance());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}

		/* 18-Oct-2010, tatu: [JACKSON-351] @JsonRawValue handled just here, for now;
		 *  if we need to get raw indicator from other sources need to add
		 *  separate accessor within {@link AnnotationIntrospector} interface.
		 */
		JsonRawValue annRaw = am.getAnnotation(JsonRawValue.class);
		if ((annRaw != null) && annRaw.value()) {
			// let's construct instance with nominal type:
			Class<?> cls = am.getRawType();
			return new RawSerializer<Object>(cls);
		}
		return null;
	}

	@Override
	public Object findKeySerializer(Annotated am) {
		JsonSerialize ann = am.getAnnotation(JsonSerialize.class);
		if (ann != null) {
			Class<? extends JsonSerializer<?>> serClass = ann.keyUsing();
			if (serClass != JsonSerializer.None.class) {
				try {
					return new JsonSerializer1To2Wrapper<Object>((JsonSerializer<Object>) serClass.newInstance());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		return null;
	}

	@Override
	public Object findContentSerializer(Annotated am) {
		JsonSerialize ann = am.getAnnotation(JsonSerialize.class);
		if (ann != null) {
			Class<? extends JsonSerializer<?>> serClass = ann.contentUsing();
			if (serClass != JsonSerializer.None.class) {
				try {
					return new JsonSerializer1To2Wrapper<Object>((JsonSerializer<Object>) serClass.newInstance());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		return null;
	}

	/*
	 * handling of deserializers
	 */
	@Override
	public Object findDeserializer(Annotated am) {
		/* 21-May-2009, tatu: Slight change; primary annotation is now
		 *    @JsonDeserialize; @JsonUseDeserializer is deprecated
		 */
		JsonDeserialize ann = am.getAnnotation(JsonDeserialize.class);
		if (ann != null) {
			Class<? extends JsonDeserializer<?>> deserClass = ann.using();
			if (deserClass != JsonDeserializer.None.class) {
				try {
					return new JsonDeserializer1To2Wrapper<Object>((JsonDeserializer<Object>) deserClass.newInstance());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		// 31-Jan-2010, tatus: @JsonUseDeserializer removed as of 1.5
		return null;
	}

	@Override
	public Object findKeyDeserializer(Annotated am) {
		JsonDeserialize ann = am.getAnnotation(JsonDeserialize.class);
		if (ann != null) {
			Class<? extends KeyDeserializer> deserClass = ann.keyUsing();
			if (deserClass != KeyDeserializer.None.class) {
				try {
					return new KeyDeserializer1To2Wrapper(deserClass.newInstance());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		return null;
	}

	@Override
	public Object findContentDeserializer(Annotated am) {
		JsonDeserialize ann = am.getAnnotation(JsonDeserialize.class);
		if (ann != null) {
			Class<? extends JsonDeserializer<?>> deserClass = ann.contentUsing();
			if (deserClass != JsonDeserializer.None.class) {
				try {
					return new JsonDeserializer1To2Wrapper<Object>((JsonDeserializer<Object>) deserClass.newInstance());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		return null;
	}

	@Override
	public Object findFilterId(AnnotatedClass ac) {
		JsonFilter ann = ac.getAnnotation(JsonFilter.class);
		if (ann != null) {
			String id = ann.value();
			// Empty String is same as not having annotation, to allow overrides
			if (id.length() > 0) {
				return id;
			}
		}
		return null;
	}

	@Override
	public Object findInjectableValueId(AnnotatedMember m) {
		JacksonInject ann = m.getAnnotation(JacksonInject.class);
		if (ann == null) {
			return null;
		}
		/* Empty String means that we should use name of declared
		 * value class.
		 */
		String id = ann.value();
		if (id.length() == 0) {
			// slight complication; for setters, type
			if (!(m instanceof AnnotatedMethod)) {
				return m.getRawType().getName();
			}
			AnnotatedMethod am = (AnnotatedMethod) m;
			if (am.getParameterCount() == 0) {
				return m.getRawType().getName();
			}
			return am.getParameter(0).getName();
		}
		return id;
	}

}
