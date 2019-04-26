package de.code_freak.codefreak.service

import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.DockerClient.ListContainersParam
import de.code_freak.codefreak.SpringTest
import de.code_freak.codefreak.entity.Answer
import de.code_freak.codefreak.util.TarUtil
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import java.util.UUID

internal class ContainerServiceTest : SpringTest() {

  @Autowired
  lateinit var docker: DockerClient

  @Autowired
  lateinit var containerService: ContainerService

  val answer by lazy {
    val mock = mock(Answer::class.java)
    `when`(mock.id).thenReturn(UUID(0, 0))
    mock
  }

  @Before
  fun pullImages() {
    containerService.pullDockerImages()
  }

  @Before
  @After
  fun tearDown() {
    // delete all containers before and after each run
    getAllIdeContainers().parallelStream().forEach {
      docker.killContainer(it.id())
      docker.removeContainer(it.id())
    }
  }

  @Test
  fun `New IDE container is started`() {
    containerService.startIdeContainer(answer)
    val container = getIdeContainer(answer) // throws if container is not present
    assertTrue(docker.inspectContainer(container.id()).state().running())
  }

  @Test
  fun `Existing IDE container is used`() {
    containerService.startIdeContainer(answer)
    containerService.startIdeContainer(answer) // start twice for the same answer
    assertThat(getIdeContainers(answer), hasSize(1))
  }

  @Test
  fun `files are extracted to project directory`() {
    `when`(answer.files).thenReturn(TarUtil.createTarFromDirectory(ClassPathResource("tasks/c-simple").file))
    containerService.startIdeContainer(answer)
    val containerId = getIdeContainer(answer).id()
    // assert that file is existing and nothing is owned by root
    val dirContent = containerService.exec(containerId, arrayOf("ls", "-l", "/home/project"))
    assertThat(dirContent, containsString("main.c"))
    assertThat(dirContent, not(containsString("root")))
  }

  private fun getAllIdeContainers() = docker.listContainers(
      ListContainersParam.withLabel(ContainerService.LABEL_ANSWER_ID)
  )

  private fun getIdeContainers(answer: Answer) = docker.listContainers(
      ListContainersParam.withLabel(ContainerService.LABEL_ANSWER_ID, answer.id.toString())
  )

  private fun getIdeContainer(answer: Answer) = getIdeContainers(answer).first()
}
