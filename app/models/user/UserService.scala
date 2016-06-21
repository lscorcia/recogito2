package models.user

import java.math.BigInteger
import java.security.MessageDigest
import java.sql.Timestamp
import java.util.Date
import models.{ BaseService, Page }
import models.generated.Tables._
import models.generated.tables.records.{ UserRecord, UserRoleRecord }
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.FileUtils
import play.api.cache.CacheApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.collection.JavaConversions._
import scala.concurrent.Future
import storage.{ DB, FileAccess }
import sun.security.provider.SecureRandom

object UserService extends BaseService with FileAccess {

  private val SHA_256 = "SHA-256"

  def listUsers(offset: Int = 0, limit: Int = 20)(implicit db: DB) = db.query { sql =>
    val startTime = System.currentTimeMillis
    val total = sql.selectCount().from(USER).fetchOne(0, classOf[Int])
    val users = sql.selectFrom(USER).limit(limit).offset(offset).fetch().into(classOf[UserRecord])
    Page(System.currentTimeMillis - startTime, total, offset, limit, users.toSeq)
  }

  def insertUser(username: String, email: String, password: String)(implicit db: DB) = db.withTransaction { sql =>
    val salt = randomSalt
    val user = new UserRecord(username, email, computeHash(salt + password), salt, new Timestamp(new Date().getTime), true)
    sql.insertInto(USER).set(user).execute()
    user
  }

  /** This method is cached, since it's basically called on every request **/
  def findByUsername(username: String)(implicit db: DB, cache: CacheApi) =
    cachedLookup("user", username, findByUsernameNoCache)

  def findByUsernameNoCache(username: String)(implicit db: DB) = db.query { sql =>
    val records =
      sql.selectFrom(USER.naturalLeftOuterJoin(USER_ROLE))
         .where(USER.USERNAME.equal(username))
         .fetchArray()

    groupLeftJoinResult(records, classOf[UserRecord], classOf[UserRoleRecord]).headOption
      .map { case (user, roles) => UserWithRoles(user, roles) }
  }

  def findByUsernameIgnoreCase(username: String)(implicit db: DB) = db.query { sql =>
    Option(sql.selectFrom(USER).where(USER.USERNAME.equalIgnoreCase(username)).fetchOne())
  }

  def validateUser(username: String, password: String)(implicit db: DB, cache: CacheApi) =
    findByUsername(username).map(_ match {
      case Some(userWithRoles) => computeHash(userWithRoles.user.getSalt + password) == userWithRoles.user.getPasswordHash
      case None => false
    })
    
  /** Runs a prefix search on usernames.
    *
    * To keep result size low (and add some extra 'privacy') the method only matches on
    * usernames that are at most 2 characters longer than the query.
    */
  def searchUsers(query: String)(implicit db: DB): Future[Seq[String]] = db.query { sql =>
    if (query.size > 2)
      sql.selectFrom(USER)
         .where(USER.USERNAME.like(query + "%")
           .and(USER.USERNAME.length().lt(query.size + 4)))
         .fetch()
         .getValues(USER.USERNAME, classOf[String]).toSeq
    else
      Seq.empty[String]
  }

  def getUsedDiskspaceKB(username: String) =
    getUserDir(username).map(dataDir => FileUtils.sizeOfDirectory(dataDir)).getOrElse(0l)

  /** Utility function to create new random salt for password hashing **/
  private def randomSalt = {
    val r = new SecureRandom()
    val salt = new Array[Byte](32)
    r.engineNextBytes(salt)
    Base64.encodeBase64String(salt)
  }

  /** Utility function to compute an MD5 password hash **/
  private def computeHash(str: String) = {
    val md = MessageDigest.getInstance(SHA_256).digest(str.getBytes)
    new BigInteger(1, md).toString(16)
  }

}
