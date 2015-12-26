# Annotating Model Classes
`TikXml` uses annotation processing to generate the xml serializer / deserializer (parser) for java model classes (POJO).
We have to provide a _mapping_ from java class to xml. This is done by annotating the java class. Basically you can annotate fields of your model class.
Since the generated serializer / deserializer will be in the same package as the original java model class your fields must be either:

- not _private_ or _protected_. In other words fields must be _public_ or have _default_ (package) visibility. Furthermore fields can not be _static_ or _final_.
 ```java
   public class Book {
   
     String id;  // package visibility is ok
     public String title; // public visibility is ok
   }
 ```

**or**

- fields can be private but must provide a non _private or protected getter and setter_ methods following java method naming convetion (`setFoo(Foo foo), getFoo()`)
  ```java
     public class Book {
     
       private String id;  // package visibility is ok
       
       public void setId(String id) { this.id = id; }
       public String getId() { return id; }
     
     }
   ```

## Mark a class as model class
To mark a class as serializeable / deserializeable by `TikXml` you have to annotate your model class with `@Xml`.

```java
@Xml(nameAsRoot = "book") // name is optional. Per default we use class name in lowercase
public class Book {

  String id; 
}
```

Please ignore reading (parsing) xml for a moment. This paragraph is about writing xml. If `Book` is the root object of an xml document 
we have to specify a name for that root xml element. Per default we use the class name in lowercase, 
but you can customize it within the `@Xml( nameAsRoot = "foo")` annotation.


## XML Element attributes
Reading and writing the following xml:

```xml
<book id="123"></book>
```

```java
@Xml
public class Book {

  @Attribute(name = "id") // name is optional, per default the field name will be used as name
  String id; 
}
```

## Type Converter
`@Attribute` can be read and write primitives like `int`, `long`, `double`, `boolean` and `String` (and wrapper classes like `Integer`, `Long`, `Double`). Additionally, to this build in types you can specify your own type converter that takes the xml attribute's String value as input and convert it to the desired type:
 

```xml
<book id="123" publish_date="2015-11-25"></book>
```

```java
@Xml
public class Book {

  @Attribute
  String id; 
  
  @Attribute(name = "publish_date", converter = MyDateConverter.class)
  Date published; 
}
```

```java
public class MyDateConverter implements Converter<Date> {

  private SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd"); // SimpleDateFormat is not thread safe!

  @Override
  public Date read(String value) throws Exception {
    return formatter.parse(value);
  }
  
  @Override
  public String write(Date value) throws Exception {
    return formatter.format(value);
  }
  
}
```

Your custom `Converter` must provide an empty (parameter less) constructor).
As you see, you can specify a custom converter for each field with `@Attribute(converter = MyConverter.class)`. 
Additionally, you can set default converter for all your xml feeds directly in `TikXml`. 

```java
TikXml parser = new TikXml(); // Xml serializer / deserializer
parser.setConverter(Date.class, new MyDateConverter() ); // all fields of type Date will be serialized / deserialized by using MyDateConverter
```

If you set a default converter you can still apply another converter on a specific field via annotation.
The converter specified in the annotation will be used instead of the default converter.

 
## Property Elements
In XML not only attributes can be used to model properties but also nested elements like this:
```xml
<book id="123">
  <title>Effective Java</title>
  <author>Joshua Bloch</author>
  <publish_date>2015-11-25</publish_date>
</book>
```

In java we have to annotate the `Book` class by using `@PropertyElement`:

```java
@Xml
public class Book {

  @Attribute
  String id; 
  
  @PropertyElement
  String title;
  
  @PropertyElement
  String author;
  
  @PropertyElement(name = "publish_date", converter = MyDateConverter.class)
  Date published; 
}
```

The `@PropertyElement` annotation is similar to the `@Attribute` annotation. You can optionally specify a `name` (otherwise field name will be used) and a `converter`. 
The converters can be shared between `@Attribute` and `@PropertyElement`. So you can use your custom converter like `MyDateConverter` for both, `@Attribute` and `@PropertyElement`.
Also, a default converter set with `tikXml.setConverter(Date.class, new MyDateConverter() );` will be used for both as well.

## Child Elements
In XML you can nest child element in elements. You have already seen that in `@PropertyElement`. 
However, property elements are there read just the text content of an element and meant to be used 
for primitives like `int`, `double`, `String` etc. (eventually other "simple" data types like `Date` via custom `TypeConverter`).

If you want to parse or write child elements (or child objects) then `@Element` is the annotation you are looking for:

```xml
<book id="123">
  <title>Effective Java</title>
  <author>     <!-- child element -->
    <firstname>Joshua</firstname>
    <lastname>Bloch</lastname>
  </author>
</book>
```

```java
@Xml
public class Book {

  @Attribute
  String id; 
  
  @PropertyElement
  String title;
 
  @Element(name = "author") // name is optional, field name will be used as default value
  Author author;
}


@Xml
public class Author {
  
  @PropertyElement
  String firstname;
  
  @PropertyElement
  String lastname;
}
```

`TikXml` will write and parse an instance of `Author` automatically for you. 

## Polymorphism and inheritance
`TikXml` supports polymorphism and inheritance. However, you have to specify that explicitly 
via annotations similar to other parser like jackson (json parser).

```java
@Xml
class Author {

  @PropertyElement
  String firstname;
  
  @PropertyElement
  String lastname;
  
}


@Xml(inheritance = true) // inherits firstname and lastname xml properties. Default value is true
public class Journalist extends Author {

  @PropertyElement(name = "newspaper_publisher")
  String newspaperPublisher; // the name of the newspaper the Journalist works for
}
```

`Author` has firstname and lastname fields. Since `Journalist extends Author` Journalist (java class) of course  
has inherited firstname and lastname fields as well. That also means that the xml representation of an Journalist 
has firstname and lastname xml elements. This is usually the desired behaviour. However, you can 
disable that via `@Xml(inheritance = false)`. If you set `inheritance = false` Journalist's xml representation
will not have the inherited properties from super class (firstname and lastname), but only the properties 
defined in the Journalist class itself (only newspaperPublisher). 
Lets say that a book can be written by either a `Author` or an `Journalist`:

```java
@Xml
public class Book {

  @Attribute
  String id; 
  
  @PropertyElement
  String title;
 
  @Element(
    name = "author", // optional, otherwise field name will be used
    typesByElement = @ElementNameMatcher( elementName = "journalist", type = Journalist.class)
  )
  Author author;
}
```

So`@Element(typesByElement = @ElementNameMatcher)` is where we have to define how we determine polymorphism while reading xml.
With `@ElementNameMatcher(elementName = "journalist", type = Author.class)` we define that, if the xml element name is `journalist` we are going to parse an `Journalist` object.

```xml
<book id="111">
  <title>Android for Dummies</title>
  <Journalist>
    <firstname>Hannes</firstname>
    <lastname>Dorfmann</lastname>
    <newspaper_publisher>New York Times</newspaper_publisher>
  </Journalist>
</book>
```

Otherwise, if we want to parse an `Author` we expect an xml element with the name `author` 
because the normal `@Element(name="author")` definition will apply.

```xml
<book id="123">
  <title>Effective Java</title>
  <author>
    <firstname>Joshua</firstname>
    <lastname>Bloch</lastname>
  </author>
</book>
```

We can also do that with java `Interfaces`:

```java
interface Writer {
  int getId();
}

@Xml
class Author implements Writer {

  @Attribute
  String id; 
    
  @PropertyElement
  String firstname;
  
  @PropertyElement
  String lastname;

}

class Organization implements Writer {

  @PropertyElement
  String id; 
    
  @PropertyElement
  String name;
 
}
```

Now a Book expects an `Writer`:
```java
@Xml
public class Book {

  @Attribute
  String id; 
  
  @PropertyElement
  String title;
 
  @Element(
    typesByElement = {
      @ElementNameMatcher( elementName = "author", type = Author.class),
      @ElementNameMatcher( elementName = "organization", type = Organization.class),
    }
  )
  Writer writer;
}
```

Now `TikXml` can read both xml variants:
```xml
<book id="123">
  <title>Effective Java</title>
  <author id="1">
    <firstname>Joshua</firstname>
    <lastname>Bloch</lastname>
  </author>
</book>
```

and

```xml
<book id="123">
  <title>end-of-year review</title>
  <organization>
    <id>23</id>
    <name>New York Times</name>
  </organization>
</book>
```

As you see, you can define arbitrary many `@ElementNameMatcher` to resolve polymorphism. 
Since resolution of polymorphism is done by **checking the xml element name** the `<book>` can only have 
one single `<author />` tag, because we can't use xml element's name as property anymore (as we did with `@PropertyElement`).

Therefore something like this is not valid:

```java
@Xml
public class Book {

  @Attribute
  String id; 
  
  @PropertyElement
  String title;
 
  @Element(
    typesByElement = {
      @TypMatcher( elementName = "author", type = Author.class),
      @TypMatcher( elementName = "organization", type = Organization.class),
    }
  )
  Writer writer1;
  
  @Element(
      typesByElement = {
        @TypMatcher( elementName = "author", type = Author.class),
        @TypMatcher( elementName = "organization", type = Organization.class),
      }
    )
    Writer writer2;
}
```

```xml
<book id="123">
  <title>Effective Java</title>
  <author id="1">
    <firstname>Joshua</firstname>
    <lastname>Bloch</lastname>
  </author>
  
  <author id="2">
    <firstname>Hannes</firstname>
    <lastname>Dorfmann</lastname>
  </author>
</book>
```

The parser can't know whether `<author>` maps to `writer1` or `writer2`. 
We are aware of this limitation and might fix that in a future version (i.e. we could add `@TypeMatcher` support for xml attributes to determine the java type). However, we think that this 
is not a common use case. Usually you deal with multiple elements in form of a list.


## List of elements
If we want to have a List of child elements we use `@Element` on `java.util.List`:

```java
@Xml
class Catalogue {

  @Element(name = "books") // optional name of xml element, otherwise field name is used
  List<Book> books;

}
```

which will read and write the following xml:

```xml
<catalog>
  <books>
    <book id="1">...</book>
    <book id="2">...</book>
    <book id="3">...</book>
  </books>
</catalog>
```

With `@Element( typesByElement = @ElementNameMatcher() )` you can deal with polymorphism for lists the same way as shown above.

## Inline list of elements
As you have seen in the section before our xml includes an extra `<books>` tag, 
where the list of `<book>` element is in. We can remove this surrounding tag with the additional `@InlineList` annotation.
This will result in a flatten list like this:

```xml
<catalog>
    <book id="1">...</book>
    <book id="2">...</book>
    <book id="3">...</book>
</catalog>
```


```java
@Xml
class Catalogue {

  @InlineList
  @Element
  List<Book> books;

}
```


## Paths 
Have a look at the following example: Imagine we have a xml representation of a bookstore with only one single book and one single newspaper:
```xml
<shop>
  <bookstore name="Lukes bookstore">
    <inventory>
      <book title="Effective Java" />
      <newspaper title="New York Times n. 192" />
    </inventory>
  </bookstore>
</shop>
```

To parse that into java classes we would have to add a `Bookstore` class and a `Inventory` 
class to be able to parse that kind of blown up xml. This isn't really memory efficient because we 
have to instantiate `Bookstore` and `Inventory` to access `<book>` and `<newspaper>`.
With `@Path` We can do that **emulate** this xml nodes:

```java
@Xml
class Shop {

  @Path("bookstore[name]") // attributes name between '[' and ']'
  @Attribute
  String name
  
  @Path("bookstore/inventory")  //  '/' indicates child element
  @Element
  Book book;
  
  @Path("bookstore/inventory")
  @Element
  Newspaper newspaper;
    
}
```

`TikXml` will read `<bookstore>` and `<inventory>` without the extra cost of allocating such an object.
It will also take that "virtual" nodes into account when writing xml.

Please note that this is not **XPath**. It looks similar to XPath, but XPath is not supported by `TikXml`.

## Text Content
In XML the content of an xml element can be a mix of text and child elements:
```xml
<book>
  <title>Effective Java</title>
  <author>
    <name>Joshua Bloch</name>
  </author>
  This book talks about tips and tricks and best practices for java developers
</book>
```

This is valid XML. We have an description text directly embedded `<book></book>`. But how do we read that description text? 
In that use case we can use `@TextContent` for reading and writing such xml:

```java
@Xml
class Book {

  @PropertyElement
  String title;
  
  @Element
  Author author;
  
  @TextContent
  String description; // Contains the text "This book talks about ..."
}
```

You can think of `@TextContent` as some kind of inline `@PropertyElement`. `@TextContent` 
reads the whole text content of a XML element even if there are other xml elements in between:

```xml
<book>

  This book talks 
  
  <title>Effective Java</title>
  
  about tips and tricks
  
  <author>
    <name>Joshua Bloch</name>
  </author>
 
  and best practices for java developers
  
</book>
```

## Scan Modes
As you see there are quite some annotations. Usually programmers are lazy people. Therefore we provide two modes.
 1. **ANNOTATION_ONLY**: This means that only fields with annotations like `@Attribute`, `@Element`, `@PropertyElement`, `@TextContent` will be used. Any other fields are not be taken into account when scanning for xml mappings.
 2. **COMMON_CASE**: The "common case" means all primitive java data types (like int, double, string) are mapped to xml attributes (is equal to
annotating class fields with `@Attribute`. All non primitive types (in other words objects) are mapped to child objects (is equal to annotating class fields with {@link
  Example: 
 
  ```xml
   <book id="123" title="Effective java"> `
    <author>...</author> 
   </book> ``
  ```
  By using COMMON_CASE you don't have to write much annotations:
  
  ```java
  @Xml(mode = ScanMode.COMMON_CASE) 
  class Book { 
    int id;          // Doesn't need an @Attribute
    String title;    // Doesn't need an @Attribute 
    Author author;   // Doesn't need an @Element
    
    @IgnoreXml
    double calculatedPrice; // Will be ignored
  }
  ```
