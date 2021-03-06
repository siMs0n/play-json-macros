package tech.minna.playjson.macros

import scala.reflect.macros.blackbox

object ExtendCompanionObject {
  def impl(c: blackbox.Context)(annottees: Seq[c.Expr[Any]])(formatter: (c.universe.TypeName, List[c.universe.ValDef]) => c.universe.Tree): c.Expr[Any] = {
    import c.universe._

    def modifiedDeclaration(classDecl: ClassDef, compDeclOpt: Option[ModuleDef] = None) = {
      val (className, fields) = extractClassNameAndFields(classDecl)
      val formatTree = formatter(className, fields)
      val formatField = q"""implicit val jsonFormat = $formatTree"""
      val compDecl = addFormatterToCompanionObject(compDeclOpt, formatField, className)

      c.Expr {
        q"""
          $classDecl
          $compDecl
        """
      }
    }

    def extractClassNameAndFields(classDecl: c.universe.ClassDef) = {
      try {
        val q"case class $className(..$fields) extends ..$bases { ..$body }" = classDecl
        (className, fields)
      } catch {
        case _: MatchError => c.abort(c.enclosingPosition, "Annotation is only supported on case class")
      }
    }

    def addFormatterToCompanionObject(compDeclOpt: Option[ModuleDef], formatDef: ValDef, className: TypeName) = {
      compDeclOpt match {
        case Some(compDecl) =>
          // Add the formatter to the existing companion object
          val q"object $obj extends ..$bases { ..$body }" = compDecl
          q"""
            object $obj extends ..$bases {
              ..$body
              $formatDef
            }
          """
        case None =>
          // Create a companion object with the formatter
          q"object ${className.toTermName} { $formatDef }"
      }
    }

    annottees.map(_.tree) match {
      case (classDecl: ClassDef) :: Nil => modifiedDeclaration(classDecl)
      case (classDecl: ClassDef) :: (compDecl: ModuleDef) :: Nil => modifiedDeclaration(classDecl, Some(compDecl))
      case _ => c.abort(c.enclosingPosition, "Invalid annottee")
    }
  }
}
