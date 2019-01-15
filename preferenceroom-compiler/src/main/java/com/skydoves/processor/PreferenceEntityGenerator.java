/*
 * Copyright (C) 2017 skydoves
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.skydoves.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.util.Elements;

import androidx.annotation.NonNull;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

@SuppressWarnings("WeakerAccess")
public class PreferenceEntityGenerator {

    private final PreferenceEntityAnnotatedClass annotatedClazz;
    private final Elements annotatedElementUtils;

    private static final String CLAZZ_PREFIX = "Preference_";
    private static final String FIELD_PREFERENCE = "preference";
    private static final String FIELD_INSTANCE = "instance";
    private static final String CONSTRUCTOR_CONTEXT = "context";
    private static final String KEY_NAME_LIST = "keyNameList";

    private static final String EDIT_METHOD = "edit()";
    private static final String CLEAR_METHOD = "clear()";
    private static final String APPLY_METHOD = "apply()";

    private static final String PACKAGE_CONTEXT = "android.content.Context";
    private static final String PACKAGE_SHAREDPREFERENCE = "android.content.SharedPreferences";
    private static final String PACKAGE_PREFERENCEMANAGER = "android.preference.PreferenceManager";

    public PreferenceEntityGenerator(@NonNull PreferenceEntityAnnotatedClass annotatedClass, @NonNull Elements elementUtils) {
        this.annotatedClazz = annotatedClass;
        this.annotatedElementUtils = elementUtils;
    }

    public TypeSpec generate() {
        TypeSpec.Builder builder = TypeSpec.classBuilder(getClazzName())
                .addJavadoc("Generated by PreferenceRoom. (https://github.com/skydoves/PreferenceRoom).\n")
                .addModifiers(PUBLIC)
                .superclass(ClassName.get(annotatedClazz.annotatedElement))
                .addFields(getFieldSpecs());

                if(annotatedClazz.isDefaultPreference) {
                    builder.addMethod(getDefaultPreferenceConstructorSpec());
                } else {
                    builder.addMethod(getConstructorSpec());
                }

                builder.addMethod(getInstanceSpec())
                       .addMethods(getFieldMethodSpecs())
                       .addMethod(getClearMethodSpec())
                       .addMethod(getKeyNameListMethodSpec())
                       .addMethod(getEntityNameMethodSpec())
                       .addTypes(getOnChangedTypeSpecs());

        return builder.build();
    }

    private List<FieldSpec> getFieldSpecs() {
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        fieldSpecs.add(FieldSpec.builder(getSharedPreferencesPackageType(), FIELD_PREFERENCE, PRIVATE, FINAL).build());
        fieldSpecs.add(FieldSpec.builder(getClassType(), FIELD_INSTANCE, PRIVATE, STATIC).build());
        return fieldSpecs;
    }

    private MethodSpec getConstructorSpec() {
        return MethodSpec.constructorBuilder()
                .addModifiers(PRIVATE)
                .addParameter(ParameterSpec.builder(getContextPackageType(), CONSTRUCTOR_CONTEXT).addAnnotation(NonNull.class).build())
                .addStatement("$N = $N.getSharedPreferences($S, Context.MODE_PRIVATE)", FIELD_PREFERENCE, CONSTRUCTOR_CONTEXT, annotatedClazz.entityName)
                .build();
    }

    private MethodSpec getDefaultPreferenceConstructorSpec() {
        return MethodSpec.constructorBuilder()
                .addModifiers(PRIVATE)
                .addParameter(ParameterSpec.builder(getContextPackageType(), CONSTRUCTOR_CONTEXT).addAnnotation(NonNull.class).build())
                .addStatement("$N = $T.getDefaultSharedPreferences($N)", FIELD_PREFERENCE, getPreferenceManagerPackageType() ,CONSTRUCTOR_CONTEXT)
                .build();
    }

    private MethodSpec getInstanceSpec() {
        return MethodSpec.methodBuilder("getInstance")
                .addModifiers(PUBLIC, STATIC)
                .addParameter(ParameterSpec.builder(getContextPackageType(), CONSTRUCTOR_CONTEXT).addAnnotation(NonNull.class).build())
                .addStatement("if($N != null) return $N", FIELD_INSTANCE, FIELD_INSTANCE)
                .addStatement("$N = new $N($N)", FIELD_INSTANCE, getClazzName(), CONSTRUCTOR_CONTEXT)
                .addStatement("return $N", FIELD_INSTANCE)
                .returns(getClassType())
                .build();
    }

    private List<MethodSpec> getFieldMethodSpecs() {
        List<MethodSpec> methodSpecs = new ArrayList<>();
        this.annotatedClazz.keyFields.forEach(annotatedFields -> {
            PreferenceFieldMethodGenerator methodGenerator = new PreferenceFieldMethodGenerator(annotatedFields, annotatedClazz, FIELD_PREFERENCE);
            methodSpecs.addAll(methodGenerator.getFieldMethods());
        });
        return methodSpecs;
    }

    private MethodSpec getClearMethodSpec() {
        return MethodSpec.methodBuilder("clear")
                .addModifiers(PUBLIC)
                .addStatement("$N.$N.$N.$N", FIELD_PREFERENCE, EDIT_METHOD, CLEAR_METHOD, APPLY_METHOD)
                .build();
    }

    private MethodSpec getKeyNameListMethodSpec() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("get" + KEY_NAME_LIST)
                .addModifiers(PUBLIC)
                .returns(List.class)
                .addStatement("List<String> $N = new $T<>()", KEY_NAME_LIST, ArrayList.class);

        this.annotatedClazz.keyNameFields.forEach(keyName -> builder.addStatement("$N.add($S)", KEY_NAME_LIST, keyName));

        builder.addStatement("return $N", KEY_NAME_LIST);
        return builder.build();
    }

    private MethodSpec getEntityNameMethodSpec() {
        return MethodSpec.methodBuilder("getEntityName")
                .addModifiers(PUBLIC)
                .returns(String.class)
                .addStatement("return $S", annotatedClazz.entityName)
                .build();
    }

    private List<TypeSpec> getOnChangedTypeSpecs() {
        List<TypeSpec> typeSpecs = new ArrayList<>();
        this.annotatedClazz.keyFields.forEach(annotatedFields -> {
            PreferenceChangeListenerGenerator changeListenerGenerator = new PreferenceChangeListenerGenerator(annotatedFields);
            typeSpecs.add(changeListenerGenerator.generate());
        });
        return typeSpecs;
    }

    private ClassName getClassType() {
        return ClassName.get(annotatedClazz.packageName, getClazzName());
    }

    private String getClazzName() {
        return CLAZZ_PREFIX + annotatedClazz.entityName;
    }

    private TypeName getContextPackageType() {
        return TypeName.get(annotatedElementUtils.getTypeElement(PACKAGE_CONTEXT).asType());
    }

    private TypeName getSharedPreferencesPackageType() {
        return TypeName.get(annotatedElementUtils.getTypeElement(PACKAGE_SHAREDPREFERENCE).asType());
    }

    private TypeName getPreferenceManagerPackageType() {
        return TypeName.get(annotatedElementUtils.getTypeElement(PACKAGE_PREFERENCEMANAGER).asType());
    }
}
